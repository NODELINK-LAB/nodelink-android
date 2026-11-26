// Phase 6 on-chain check: deploys WNODE, NodeMultisig and NodeVesting to a running NODELINK
// network over Ethereum JSON-RPC and exercises them with real signed transactions.
// Env: RPC (http://host:port), KEY (hex private key of a funded test account), OUT (solc output dir with .abi/.bin).
package main

import (
	"context"
	"crypto/ecdsa"
	"fmt"
	"math/big"
	"os"
	"path/filepath"
	"strings"
	"time"

	"github.com/ethereum/go-ethereum"
	"github.com/ethereum/go-ethereum/accounts/abi"
	"github.com/ethereum/go-ethereum/common"
	"github.com/ethereum/go-ethereum/core/types"
	"github.com/ethereum/go-ethereum/crypto"
	"github.com/ethereum/go-ethereum/ethclient"
)

var (
	ctx     = context.Background()
	ec      *ethclient.Client
	chainID *big.Int
	outDir  string
	fails   int
	unit    = new(big.Int).Exp(big.NewInt(10), big.NewInt(18), nil)
)

type acct struct {
	key  *ecdsa.PrivateKey
	addr common.Address
}

type contract struct {
	addr common.Address
	abi  abi.ABI
}

func node(x int64) *big.Int { return new(big.Int).Mul(big.NewInt(x), unit) }

func must(err error) {
	if err != nil {
		fmt.Println("FAIL fatal:", err)
		os.Exit(1)
	}
}

func check(name string, ok bool, detail string) {
	if ok {
		fmt.Printf("PASS %s (%s)\n", name, detail)
	} else {
		fmt.Printf("FAIL %s: %s\n", name, detail)
		fails++
	}
}

func fresh() acct {
	k, err := crypto.GenerateKey()
	must(err)
	return acct{k, crypto.PubkeyToAddress(k.PublicKey)}
}

func send(from acct, to *common.Address, value *big.Int, data []byte) *types.Receipt {
	nonce, err := ec.PendingNonceAt(ctx, from.addr)
	must(err)
	gp, err := ec.SuggestGasPrice(ctx)
	must(err)
	gas, err := ec.EstimateGas(ctx, ethereum.CallMsg{From: from.addr, To: to, Value: value, Data: data})
	must(err)
	tx := types.NewTx(&types.LegacyTx{Nonce: nonce, GasPrice: gp, Gas: gas * 3 / 2, To: to, Value: value, Data: data})
	signed, err := types.SignTx(tx, types.LatestSignerForChainID(chainID), from.key)
	must(err)
	must(ec.SendTransaction(ctx, signed))
	for i := 0; i < 60; i++ {
		if r, err := ec.TransactionReceipt(ctx, signed.Hash()); err == nil {
			if r.Status != 1 {
				must(fmt.Errorf("transaction %s reverted", signed.Hash()))
			}
			return r
		}
		time.Sleep(time.Second)
	}
	must(fmt.Errorf("transaction %s not mined in 60s", signed.Hash()))
	return nil
}

func deploy(from acct, name string, args ...interface{}) contract {
	abiJSON, err := os.ReadFile(filepath.Join(outDir, name+".abi"))
	must(err)
	bin, err := os.ReadFile(filepath.Join(outDir, name+".bin"))
	must(err)
	a, err := abi.JSON(strings.NewReader(string(abiJSON)))
	must(err)
	ctor, err := a.Pack("", args...)
	must(err)
	r := send(from, nil, nil, append(common.FromHex(strings.TrimSpace(string(bin))), ctor...))
	code, err := ec.CodeAt(ctx, r.ContractAddress, nil)
	must(err)
	check(name+" deployed", len(code) > 0, r.ContractAddress.Hex())
	return contract{r.ContractAddress, a}
}

func tx(from acct, c contract, value *big.Int, method string, args ...interface{}) {
	data, err := c.abi.Pack(method, args...)
	must(err)
	send(from, &c.addr, value, data)
}

func call(c contract, method string, args ...interface{}) interface{} {
	data, err := c.abi.Pack(method, args...)
	must(err)
	res, err := ec.CallContract(ctx, ethereum.CallMsg{To: &c.addr, Data: data}, nil)
	must(err)
	out, err := c.abi.Unpack(method, res)
	must(err)
	return out[0]
}

func reverts(from acct, c contract, method string, args ...interface{}) bool {
	data, err := c.abi.Pack(method, args...)
	must(err)
	_, err = ec.CallContract(ctx, ethereum.CallMsg{From: from.addr, To: &c.addr, Data: data}, nil)
	return err != nil
}

func bal(a common.Address) *big.Int {
	b, err := ec.BalanceAt(ctx, a, nil)
	must(err)
	return b
}

func num(v interface{}) *big.Int { return v.(*big.Int) }
func eq(a, b *big.Int) bool    { return a.Cmp(b) == 0 }

func blockTime() uint64 {
	h, err := ec.HeaderByNumber(ctx, nil)
	must(err)
	return h.Time
}

func waitUntil(t uint64) {
	for blockTime() < t {
		time.Sleep(time.Second)
	}
}

func main() {
	var err error
	ec, err = ethclient.Dial(os.Getenv("RPC"))
	must(err)
	chainID, err = ec.ChainID(ctx)
	must(err)
	outDir = os.Getenv("OUT")
	k, err := crypto.HexToECDSA(strings.TrimPrefix(strings.TrimSpace(os.Getenv("KEY")), "0x"))
	must(err)
	me := acct{k, crypto.PubkeyToAddress(k.PublicKey)}
	fmt.Println("chain id", chainID, "deployer", me.addr.Hex())

	// WNODE
	w := deploy(me, "WNODE")
	check("WNODE symbol", call(w, "symbol") == "WNODE", fmt.Sprint(call(w, "symbol")))
	tx(me, w, node(5), "deposit")
	check("wrap 5 NODE", eq(num(call(w, "balanceOf", me.addr)), node(5)), num(call(w, "balanceOf", me.addr)).String())
	tx(me, w, nil, "withdraw", node(2))
	check("unwrap 2 NODE", eq(num(call(w, "balanceOf", me.addr)), node(3)), num(call(w, "balanceOf", me.addr)).String())
	check("WNODE fully backed: contract NODE equals WNODE supply", eq(bal(w.addr), num(call(w, "totalSupply"))), bal(w.addr).String())
	check("withdraw more than balance refused", reverts(me, w, "withdraw", node(4)), "reverted")
	o := fresh()
	tx(me, w, nil, "transfer", o.addr, node(1))
	check("WNODE transfer", eq(num(call(w, "balanceOf", o.addr)), node(1)), num(call(w, "balanceOf", o.addr)).String())
	send(me, &w.addr, node(1), nil)
	check("plain send wraps", eq(num(call(w, "balanceOf", me.addr)), node(3)), num(call(w, "balanceOf", me.addr)).String())
	check("transfer to zero address refused", reverts(me, w, "transfer", common.Address{}, node(1)), "reverted")

	// NodeMultisig, 2 of 3
	o2, o3, outsider, dest := fresh(), fresh(), fresh(), fresh()
	send(me, &o2.addr, node(1), nil) // gas money for the second owner
	m := deploy(me, "NodeMultisig", []common.Address{me.addr, o2.addr, o3.addr}, big.NewInt(2))
	send(me, &m.addr, node(3), nil)
	tx(me, m, nil, "submit", dest.addr, node(1), []byte{})
	zero := big.NewInt(0)
	check("execute with 1 of 2 confirmations refused", reverts(me, m, "execute", zero), "reverted")
	check("outsider cannot submit", reverts(outsider, m, "submit", dest.addr, node(1), []byte{}), "reverted")
	check("owner cannot confirm twice", reverts(me, m, "confirm", zero), "reverted")
	tx(o2, m, nil, "confirm", zero)
	tx(me, m, nil, "execute", zero)
	check("multisig paid 1 NODE after 2 confirmations", eq(bal(dest.addr), node(1)), bal(dest.addr).String())
	check("multisig balance after payout", eq(bal(m.addr), node(2)), bal(m.addr).String())
	check("proposal cannot execute twice", reverts(me, m, "execute", zero), "reverted")

	// NodeVesting: 20 s cliff, 60 s total, 9 NODE
	ben := fresh()
	t0 := blockTime()
	v := deploy(me, "NodeVesting", ben.addr, t0, uint64(20), uint64(60))
	send(me, &v.addr, node(9), nil)
	check("nothing releasable before cliff", eq(num(call(v, "releasable")), zero), num(call(v, "releasable")).String())
	check("release before cliff refused", reverts(me, v, "release"), "reverted")
	waitUntil(t0 + 30)
	tx(me, v, nil, "release")
	part := bal(ben.addr)
	check("part released after cliff to beneficiary", part.Sign() > 0 && part.Cmp(node(9)) < 0 && eq(part, num(call(v, "released"))), part.String())
	waitUntil(t0 + 61)
	tx(me, v, nil, "release")
	check("all 9 NODE released after the end", eq(bal(ben.addr), node(9)), bal(ben.addr).String())
	check("vesting contract empty", bal(v.addr).Sign() == 0, bal(v.addr).String())

	if fails > 0 {
		fmt.Println("CONTRACTS: FAILURES ABOVE")
		os.Exit(1)
	}
	fmt.Println("CONTRACTS: ALL PASSED")
}
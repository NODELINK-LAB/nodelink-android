package network

// NODELINK end-to-end chain test (phases 1 to 5), fully in one process: four real CometBFT validators running
// nodelinkd's app, with the NODELINK genesis rules, peer-to-peer consensus and the Ethereum JSON-RPC server.
// Run: go test ./tests/network/ -run TestNodelinkChain -count=1 -v -timeout 10m

import (
	"context"
	"encoding/json"
	"math/big"
	"testing"
	"time"

	"github.com/ethereum/go-ethereum"
	"github.com/ethereum/go-ethereum/common"
	gethtypes "github.com/ethereum/go-ethereum/core/types"
	"github.com/ethereum/go-ethereum/crypto"
	"github.com/stretchr/testify/require"

	dbm "github.com/cosmos/cosmos-db"
	srvflags "github.com/cosmos/evm/server/flags"
	evmtypes "github.com/cosmos/evm/x/vm/types"

	sdkmath "cosmossdk.io/math"
	pruningtypes "github.com/cosmos/cosmos-sdk/store/v2/pruning/types"

	"github.com/cosmos/cosmos-sdk/baseapp"
	"github.com/cosmos/cosmos-sdk/client"
	"github.com/cosmos/cosmos-sdk/client/flags"
	servertypes "github.com/cosmos/cosmos-sdk/server/types"
	simutils "github.com/cosmos/cosmos-sdk/testutil/sims"
	sdk "github.com/cosmos/cosmos-sdk/types"
	banktypes "github.com/cosmos/cosmos-sdk/x/bank/types"
	distrtypes "github.com/cosmos/cosmos-sdk/x/distribution/types"
	stakingtypes "github.com/cosmos/cosmos-sdk/x/staking/types"

	evmd "github.com/nodelink/nodelink/blockchain/nodelinkd"
)

const (
	nlChainID    = "nodelink-1"
	nlEVMChainID = 778001
	nlDenom      = "anode"
)

func nodeAmt(n int64) sdkmath.Int { return sdkmath.NewInt(n).Mul(sdkmath.NewInt(1_000_000_000_000_000_000)) }

// editGenesis changes one module's genesis JSON, like the jq edits in scripts/lib/genesis.sh.
func editGenesis(t *testing.T, cfg *Config, module string, f func(m map[string]any)) {
	m := map[string]any{}
	require.NoError(t, json.Unmarshal(cfg.GenesisState[module], &m))
	f(m)
	bz, err := json.Marshal(m)
	require.NoError(t, err)
	cfg.GenesisState[module] = bz
}

func nodelinkConfig(t *testing.T, user sdk.AccAddress) Config {
	// cosmos/evm keeps one EVM chain config per process and lets it be set again only while it holds the default id.
	// Four validators share this process, so 778001 is made the default here. Real nodes are separate processes.
	evmtypes.DefaultEVMChainID = nlEVMChainID
	cfg := DefaultConfig()
	cfg.ChainID, cfg.EVMChainID, cfg.BondDenom, cfg.NumValidators = nlChainID, nlEVMChainID, nlDenom, 4
	cfg.MinGasPrices = "0" + nlDenom
	cfg.TimeoutCommit = time.Second
	cfg.PruningStrategy = pruningtypes.PruningOptionNothing
	cfg.AccountTokens = sdkmath.NewInt(1)  // unrelated test denom per node
	cfg.StakingTokens = nodeAmt(25_000_000)    // genesis-validators: 4 x 25M = 100M NODE
	cfg.BondedTokens = nodeAmt(1_000_000)      // self-bond in each gentx, as in localnet-multi.sh
	cfg.AppConstructor = func(val Validator) servertypes.Application {
		return evmd.NewExampleApp(val.Ctx.Logger, dbm.NewMemDB(), true,
			simutils.AppOptionsMap{flags.FlagHome: val.Ctx.Config.RootDir, srvflags.EVMChainID: uint64(nlEVMChainID)},
			baseapp.SetPruning(pruningtypes.NewPruningOptionsFromString(val.AppConfig.Pruning)),
			baseapp.SetMinGasPrices(val.AppConfig.MinGasPrices), baseapp.SetChainID(nlChainID))
	}
	reserve := evmd.RewardReserveAddress().String()
	editGenesis(t, &cfg, "auth", func(m map[string]any) {
		m["accounts"] = append(m["accounts"].([]any),
			map[string]any{"@type": "/cosmos.auth.v1beta1.ModuleAccount", "name": evmd.RewardReserveName, "permissions": []any{},
				"base_account": map[string]any{"address": reserve, "pub_key": nil, "account_number": "0", "sequence": "0"}},
			map[string]any{"@type": "/cosmos.auth.v1beta1.BaseAccount", "address": user.String(), "pub_key": nil, "account_number": "0", "sequence": "0"})
	})
	editGenesis(t, &cfg, "bank", func(m map[string]any) {
		m["balances"] = []any{
			map[string]any{"address": reserve, "coins": []any{map[string]any{"denom": nlDenom, "amount": nodeAmt(200_000_000).String()}}},
			// The other eight allocations (700M NODE) held by one test account the EVM checks spend from.
			map[string]any{"address": user.String(), "coins": []any{map[string]any{"denom": nlDenom, "amount": nodeAmt(700_000_000).String()}}},
		}
		m["supply"] = []any{}
		m["denom_metadata"] = []any{map[string]any{"description": "NODE, native coin of the NODELINK chain", "base": nlDenom, "display": "NODE", "name": "NODE", "symbol": "NODE",
			"denom_units": []any{map[string]any{"denom": nlDenom, "exponent": 0}, map[string]any{"denom": "NODE", "exponent": 18}}}}
	})
	editGenesis(t, &cfg, "evm", func(m map[string]any) { m["params"].(map[string]any)["evm_denom"] = nlDenom })
	editGenesis(t, &cfg, "erc20", func(m map[string]any) {
		m["native_precompiles"] = []any{"0xEeeeeEeeeEeEeeEeEeEeeEEEeeeeEeeeeeeeEEeE"}
		m["token_pairs"] = []any{map[string]any{"erc20_address": "0xEeeeeEeeeEeEeeEeEeEeeEEEeeeeEeeeeeeeEEeE", "denom": nlDenom, "enabled": true, "contract_owner": "OWNER_MODULE"}}
	})
	editGenesis(t, &cfg, "slashing", func(m map[string]any) {
		p := m["params"].(map[string]any)
		p["signed_blocks_window"], p["min_signed_per_window"], p["downtime_jail_duration"] = "20", "0.500000000000000000", "30s"
		p["slash_fraction_downtime"], p["slash_fraction_double_sign"] = "0.010000000000000000", "0.050000000000000000"
	})
	return cfg
}

func TestNodelinkChain(t *testing.T) {
	key, err := crypto.GenerateKey()
	require.NoError(t, err)
	from := crypto.PubkeyToAddress(key.PublicKey)
	user := sdk.AccAddress(from.Bytes())

	net, err := New(t, t.TempDir(), nodelinkConfig(t, user))
	require.NoError(t, err)
	defer net.Cleanup()
	v0, ctx := net.Validators[0], context.Background()
	at := func(h int64) client.Context { return v0.ClientCtx.WithHeight(h) }
	supply := func(c client.Context) sdkmath.Int {
		r, err := banktypes.NewQueryClient(c).SupplyOf(ctx, &banktypes.QuerySupplyOfRequest{Denom: nlDenom})
		require.NoError(t, err)
		return r.Amount.Amount
	}
	balance := func(c client.Context, a sdk.AccAddress) sdkmath.Int {
		r, err := banktypes.NewQueryClient(c).Balance(ctx, &banktypes.QueryBalanceRequest{Address: a.String(), Denom: nlDenom})
		require.NoError(t, err)
		return r.Balance.Amount
	}
	validator := func(i int) stakingtypes.Validator {
		r, err := stakingtypes.NewQueryClient(v0.ClientCtx).Validator(ctx, &stakingtypes.QueryValidatorRequest{ValidatorAddr: net.Validators[i].ValAddress.String()})
		require.NoError(t, err)
		return r.Validator
	}
	total := nodeAmt(1_000_000_000)

	// Phases 1 and 2: four validators reach consensus over P2P.
	h, err := net.WaitForHeightWithTimeout(5, time.Minute)
	require.NoError(t, err)
	vals, err := stakingtypes.NewQueryClient(v0.ClientCtx).Validators(ctx, &stakingtypes.QueryValidatorsRequest{Status: stakingtypes.Bonded.String()})
	require.NoError(t, err)
	require.Len(t, vals.Validators, 4)
	t.Logf("PASS phase 1-2: height %d, 4 bonded validators", h)

	// Phase 3: supply fixed at every height.
	for k := int64(1); k <= h; k++ {
		require.True(t, supply(at(k)).Equal(total), "supply at height %d is %s", k, supply(at(k)))
	}
	t.Logf("PASS phase 3: supply exactly 1,000,000,000 NODE at heights 1..%d", h)

	// Phase 4: reserve pays remaining/63,115,200 each block, validators earn it.
	b1, b2 := balance(at(h-1), evmd.RewardReserveAddress()), balance(at(h), evmd.RewardReserveAddress())
	require.True(t, b1.Sub(b2).Equal(evmd.RewardPerBlock(b1)), "reserve paid %s, want %s", b1.Sub(b2), evmd.RewardPerBlock(b1))
	require.True(t, b2.LT(nodeAmt(200_000_000)))
	out := func() sdkmath.LegacyDec {
		r, err := distrtypes.NewQueryClient(v0.ClientCtx).ValidatorOutstandingRewards(ctx, &distrtypes.QueryValidatorOutstandingRewardsRequest{ValidatorAddress: v0.ValAddress.String()})
		require.NoError(t, err)
		return r.Rewards.Rewards.AmountOf(nlDenom)
	}
	o1 := out()
	require.NoError(t, net.WaitForNextBlock())
	require.NoError(t, net.WaitForNextBlock())
	require.True(t, out().GT(o1), "validator rewards did not grow")
	t.Logf("PASS phase 4 rewards: block payout %s anode (about 3.17 NODE), validator 0 outstanding %s", b1.Sub(b2), out())

	// Phase 5: Ethereum JSON-RPC, EIP-155 chain id, value transfer and contract deployment.
	eth := v0.JSONRPCClient
	require.NotNil(t, eth, "JSON-RPC not running")
	id, err := eth.ChainID(ctx)
	require.NoError(t, err)
	require.Equal(t, int64(nlEVMChainID), id.Int64())
	wei, err := eth.BalanceAt(ctx, from, nil)
	require.NoError(t, err)
	require.Equal(t, nodeAmt(700_000_000).String(), wei.String(), "EVM balance must equal bank balance, 18 decimals")
	signer := gethtypes.LatestSignerForChainID(id)
	send := func(to *common.Address, value *big.Int, data []byte, gas uint64) *gethtypes.Receipt {
		nonce, err := eth.PendingNonceAt(ctx, from)
		require.NoError(t, err)
		price, err := eth.SuggestGasPrice(ctx)
		require.NoError(t, err)
		tx, err := gethtypes.SignNewTx(key, signer, &gethtypes.LegacyTx{Nonce: nonce, To: to, Value: value, Gas: gas, GasPrice: price, Data: data})
		require.NoError(t, err)
		require.NoError(t, eth.SendTransaction(ctx, tx))
		for i := 0; i < 30; i++ {
			if r, err := eth.TransactionReceipt(ctx, tx.Hash()); err == nil {
				require.Equal(t, gethtypes.ReceiptStatusSuccessful, r.Status)
				return r
			}
			time.Sleep(time.Second)
		}
		t.Fatalf("no receipt for %s", tx.Hash())
		return nil
	}
	to := common.HexToAddress("0x000000000000000000000000000000000000c0de")
	send(&to, nodeAmt(10).BigInt(), nil, 21000)
	got, err := eth.BalanceAt(ctx, to, nil)
	require.NoError(t, err)
	require.Equal(t, nodeAmt(10).String(), got.String())
	require.True(t, balance(v0.ClientCtx, sdk.AccAddress(to.Bytes())).Equal(nodeAmt(10)), "EVM transfer must show in the bank module")
	// Counter contract, hand assembled: each call adds 1 to slot 0 and returns it.
	code := common.FromHex("0x6014600c60003960146000f3" + "60005460010160005560005460005260206000f3")
	r := send(nil, big.NewInt(0), code, 200000)
	deployed, err := eth.CodeAt(ctx, r.ContractAddress, nil)
	require.NoError(t, err)
	require.Equal(t, code[12:], deployed)
	send(&r.ContractAddress, big.NewInt(0), nil, 100000)
	slot, err := eth.StorageAt(ctx, r.ContractAddress, common.Hash{}, nil)
	require.NoError(t, err)
	require.Equal(t, int64(1), new(big.Int).SetBytes(slot).Int64())
	res, err := eth.CallContract(ctx, ethereum.CallMsg{From: from, To: &r.ContractAddress}, nil)
	require.NoError(t, err)
	require.Equal(t, int64(2), new(big.Int).SetBytes(res).Int64())
	now, err := net.LatestHeight()
	require.NoError(t, err)
	require.True(t, supply(at(now)).Equal(total), "supply changed by EVM transactions")
	t.Logf("PASS phase 5: chain id %d, 10 NODE transfer, contract %s deployed and called, supply unchanged", id, r.ContractAddress)

	// Phase 4 slashing: validator 3 goes offline, is jailed, 1% of its stake is burned.
	before, supplyBefore := validator(3).Tokens, supply(v0.ClientCtx)
	require.NoError(t, net.Validators[3].tmNode.Stop())
	for i := 0; i < 120 && !validator(3).Jailed; i++ {
		time.Sleep(time.Second)
	}
	require.True(t, validator(3).Jailed, "offline validator not jailed")
	slashed := before.Sub(validator(3).Tokens)
	require.True(t, slashed.Equal(before.QuoRaw(100)), "slashed %s of %s, want 1%%", slashed, before)
	require.NoError(t, net.WaitForNextBlock())
	require.True(t, supplyBefore.Sub(supply(v0.ClientCtx)).Equal(slashed), "supply must fall by exactly the burned slash")
	t.Logf("PASS phase 4 slashing: validator 3 jailed, %s anode (1%%) burned, chain continues with 3 of 4", slashed)
}
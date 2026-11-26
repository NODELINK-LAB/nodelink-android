#!/usr/bin/env bash
# Phase 6: build and unit-test the NODELINK contracts with Foundry, then deploy them to the running 4-validator localnet and check them on chain.
# Needs: forge and cast (https://getfoundry.sh). On-chain part runs after: build-chain.sh, localnet-multi.sh init, localnet-multi.sh start.
# ONCHAIN=0 runs only build and unit tests.
set -uo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"; source "$ROOT/blockchain/versions.env"
FAIL=0; mkdir -p "$ROOT/tests/results"; OUT="$ROOT/tests/results/phase6-$(date -u +%Y%m%dT%H%M%SZ).log"
log(){ echo "$*" | tee -a "$OUT"; }; ok(){ log "PASS $1"; }; bad(){ log "FAIL $1"; FAIL=1; }
check(){ [ "$2" = "$3" ] && ok "$1 ($2)" || bad "$1: expected $3, got $2"; }
for t in forge cast jq; do command -v "$t" >/dev/null || { echo "Missing tool: $t (install Foundry: curl -L https://foundry.paradigm.xyz | bash && foundryup)"; exit 1; }; done
cd "$ROOT/contracts"; log "NODELINK phase 6 contracts $(date -u)"; forge --version >>"$OUT" 2>&1
[ -d lib/forge-std ] || forge install foundry-rs/forge-std --no-git >>"$OUT" 2>&1
forge build >>"$OUT" 2>&1 && ok "contracts compile (solc 0.8.24)" || bad "contracts do not compile"
forge test -vv >>"$OUT" 2>&1 && ok "unit and fuzz tests (WNODE, NodeVesting, NodeMultisig)" || bad "unit tests, see $OUT"
[ "${ONCHAIN:-1}" = 0 ] && { [ "$FAIL" = 0 ] && log "RESULT: ALL CHECKS PASSED" || log "RESULT: FAILURES ABOVE"; exit "$FAIL"; }

source "$ROOT/scripts/lib/multi.sh"; RPC="http://localhost:$(evmport 0)"; KT=(--keyring-backend test --home "$NET/genesis-build")
check "EVM RPC chain id" "$(cast chain-id --rpc-url "$RPC")" "$EVM_CHAIN_ID"
KEY=$("$BIN" keys unsafe-export-eth-key alloc-community "${KT[@]}" 2>/dev/null)   # local test key only, never on a public network
[ -n "$KEY" ] || { bad "could not export the local test key"; exit 1; }
ME=$(cast wallet address --private-key "0x$KEY"); O2=$(cast wallet address --private-key 0x$(printf '%064x' 2)); O3=$(cast wallet address --private-key 0x$(printf '%064x' 3))
TXO=(--rpc-url "$RPC" --private-key "0x$KEY" --legacy --gas-price 2gwei)
SUP0=$("$BIN" q bank total-supply --node "$(node 0)" --output json | jq -r ".supply[]|select(.denom==\"$BASE_DENOM\").amount")
OWNERS="$ME,$O2,$O3" THRESHOLD=1 forge script script/Deploy.s.sol --broadcast --slow "${TXO[@]}" >>"$OUT" 2>&1 && ok "deploy script broadcast" || bad "deploy failed"
W=$(grep -m1 '^ *WNODE 0x' "$OUT" | awk '{print $2}'); M=$(grep -m1 '^ *NodeMultisig 0x' "$OUT" | awk '{print $2}')
[ "$(cast code "$W" --rpc-url "$RPC")" != 0x ] && ok "WNODE deployed at $W" || bad "no code at WNODE"
[ "$(cast code "$M" --rpc-url "$RPC")" != 0x ] && ok "NodeMultisig deployed at $M" || bad "no code at NodeMultisig"
check "WNODE symbol" "$(cast call "$W" 'symbol()(string)' --rpc-url "$RPC")" '"WNODE"'
cast send "$W" 'deposit()' --value 5ether "${TXO[@]}" >/dev/null
check "wrap 5 NODE: WNODE balance" "$(cast call "$W" 'balanceOf(address)(uint256)' "$ME" --rpc-url "$RPC" | awk '{print $1}')" "5000000000000000000"
cast send "$W" 'withdraw(uint256)' 2ether "${TXO[@]}" >/dev/null
check "unwrap 2 NODE: contract holds exactly the WNODE supply" "$(cast balance "$W" --rpc-url "$RPC")" "$(cast call "$W" 'totalSupply()(uint256)' --rpc-url "$RPC" | awk '{print $1}')"
cast send "$M" --value 3ether "${TXO[@]}" >/dev/null
cast send "$M" 'submit(address,uint256,bytes)' "$O2" 1ether 0x "${TXO[@]}" >/dev/null
cast send "$M" 'execute(uint256)' 0 "${TXO[@]}" >/dev/null
check "multisig paid 1 NODE to an owner" "$(cast balance "$O2" --rpc-url "$RPC")" "1000000000000000000"
check "multisig balance after payout" "$(cast balance "$M" --rpc-url "$RPC")" "2000000000000000000"
SUP1=$("$BIN" q bank total-supply --node "$(node 0)" --output json | jq -r ".supply[]|select(.denom==\"$BASE_DENOM\").amount")
check "contracts created no NODE: total supply unchanged" "$SUP1" "$SUP0"
[ "$FAIL" = 0 ] && log "RESULT: ALL CHECKS PASSED" || log "RESULT: FAILURES ABOVE"; log "Saved: $OUT"; exit "$FAIL"
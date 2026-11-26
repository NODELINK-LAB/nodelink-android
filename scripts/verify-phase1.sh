#!/usr/bin/env bash
# Run in a second terminal while scripts/localnet-single.sh is running.
# Checks real block production, a real bank transfer, supply, and EVM JSON-RPC. Writes tests/results/phase1-<time>.txt.
set -uo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"; source "$ROOT/blockchain/versions.env"
BIN="$ROOT/blockchain/bin/nodelinkd"; H="${NODELINK_HOME:-$ROOT/.localnet/node0}"; K=(--keyring-backend test --home "$H"); Z=000000000000000000; D="$BASE_DENOM"
mkdir -p "$ROOT/tests/results"; OUT="$ROOT/tests/results/phase1-$(date -u +%Y%m%dT%H%M%SZ).txt"; FAIL=0
log(){ echo "$*" | tee -a "$OUT"; }; check(){ if [ "$2" = "$3" ]; then log "PASS $1 ($2)"; else log "FAIL $1: expected $3, got $2"; FAIL=1; fi; }
height(){ curl -s localhost:26657/status | jq -r '.result.sync_info.latest_block_height // 0'; }
rpc(){ curl -s -X POST localhost:8545 -H 'content-type: application/json' -d "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"$1\",\"params\":$2}" | jq -r '.result'; }
log "NODELINK phase 1 verification $(date -u)"; "$BIN" version >>"$OUT" 2>&1
h1=$(height); sleep 8; h2=$(height); log "Heights: $h1 -> $h2"; [ "$h2" -gt "$h1" ] && log "PASS blocks are being produced" || { log "FAIL no new blocks"; FAIL=1; }
SUP(){ "$BIN" q bank total-supply --home "$H" --output json | jq -r ".supply[]|select(.denom==\"$D\").amount"; }
check "total supply before transfer" "$(SUP)" "1000000000$Z"
"$BIN" keys show demo-recipient "${K[@]}" >/dev/null 2>&1 || "$BIN" keys add demo-recipient --algo eth_secp256k1 "${K[@]}" >/dev/null 2>&1
TO=$("$BIN" keys show demo-recipient -a "${K[@]}"); before=$("$BIN" q bank balance "$TO" "$D" --home "$H" --output json | jq -r '.balance.amount')
HASH=$("$BIN" tx bank send alloc-community "$TO" "1$Z$D" --gas 200000 --gas-prices "2000000000$D" --chain-id "$CHAIN_ID" -y --output json "${K[@]}" | jq -r '.txhash')
log "Transfer tx: $HASH"; sleep 8
check "transfer result code" "$("$BIN" q tx "$HASH" --home "$H" --output json | jq -r '.code')" "0"
after=$("$BIN" q bank balance "$TO" "$D" --home "$H" --output json | jq -r '.balance.amount')
check "recipient received 1 NODE" "$(if command -v bc >/dev/null; then echo "${after/null/0} - ${before/null/0}" | bc; else python3 -c "print(${after/null/0} - ${before/null/0})"; fi)" "1$Z"
check "total supply after transfer (fees go to validators, nothing minted)" "$(SUP)" "1000000000$Z"
check "eth_chainId" "$(rpc eth_chainId '[]')" "$(printf '0x%x' "$EVM_CHAIN_ID")"
bn=$(rpc eth_blockNumber '[]'); log "eth_blockNumber $bn = $((bn))"; [ "$((bn))" -gt 0 ] || { log "FAIL eth_blockNumber"; FAIL=1; }
HEX=$("$BIN" debug addr "$TO" --home "$H" 2>&1 | grep -i 'Address (hex)' | awk '{print $NF}')
check "eth_getBalance matches bank balance" "$(printf '%d' "$(rpc eth_getBalance "[\"0x$HEX\",\"latest\"]")" 2>/dev/null || echo error)" "$after"
[ "$FAIL" = 0 ] && log "RESULT: ALL CHECKS PASSED" || log "RESULT: FAILURES ABOVE"; log "Saved: $OUT"; exit "$FAIL"
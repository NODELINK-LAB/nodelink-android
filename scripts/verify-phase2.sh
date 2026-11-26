#!/usr/bin/env bash
# Phase 2 checks on the 4-validator localnet. Run after: localnet-multi.sh init && localnet-multi.sh start
# Tests: all nodes agree, transfer, double spend, one validator down (continues), two down (halts), restart and catch-up, supply.
set -uo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"; source "$ROOT/blockchain/versions.env"; source "$ROOT/scripts/lib/multi.sh"
Z=000000000000000000; D="$BASE_DENOM"; KT=(--keyring-backend test --home "$NET/genesis-build"); FAIL=0
mkdir -p "$ROOT/tests/results"; OUT="$ROOT/tests/results/phase2-$(date -u +%Y%m%dT%H%M%SZ).txt"
log(){ echo "$*" | tee -a "$OUT"; }; ok(){ log "PASS $1"; }; bad(){ log "FAIL $1"; FAIL=1; }
check(){ [ "$2" = "$3" ] && ok "$1 ($2)" || bad "$1: expected $3, got $2"; }
calc(){ if command -v bc >/dev/null; then echo "scale=0; $1" | BC_LINE_LENGTH=0 bc; else python3 -c "print(int(${1//\//\/\/}))"; fi; }   # amounts exceed 64-bit
q(){ "$BIN" q "$@" --node "$(node 0)" --output json; }
SUP(){ q bank total-supply | jq -r ".supply[]|select(.denom==\"$D\").amount"; }
bal(){ q bank balance "$1" "$D" | jq -r '.balance.amount // "0"'; }   # a new account has no balance entry: count it as 0
acct(){ q auth account "$1" | jq -r "[..|objects|select(has(\"$2\"))][0].$2 // \"0\""; }
log "NODELINK phase 2 verification $(date -u)"; "$BIN" version >>"$OUT" 2>&1
sleep 10; for i in 0 1 2 3; do [ "$(height "$i")" -gt 2 ] && ok "node$i producing (height $(height "$i"))" || bad "node$i not producing"; done
h=$(( $(height 0) - 1 )); check "all 4 nodes have the same block at height $h" "$(for i in 0 1 2 3; do curl -s "localhost:$(rpcport "$i")/block?height=$h" | jq -r '.result.block_id.hash'; done | sort -u | wc -l | tr -d ' ')" "1"
check "bonded validators" "$(q staking validators | jq '[.validators[]|select(.status=="BOND_STATUS_BONDED")]|length')" "4"
check "total supply" "$(SUP)" "1000000000$Z"
for i in 0 1 2 3; do check "eth_chainId node$i" "$(curl -s -X POST "localhost:$(evmport "$i")" -H 'content-type: application/json' -d '{"jsonrpc":"2.0","id":1,"method":"eth_chainId","params":[]}' | jq -r .result)" "$(printf '0x%x' "$EVM_CHAIN_ID")"; done
"$BIN" keys show demo-recipient "${KT[@]}" >/dev/null 2>&1 || "$BIN" keys add demo-recipient --algo eth_secp256k1 "${KT[@]}" >/dev/null 2>&1
FROM=$("$BIN" keys show alloc-community -a "${KT[@]}"); TO=$("$BIN" keys show demo-recipient -a "${KT[@]}")
TX=(--gas 200000 --gas-prices "2000000000$D" --chain-id "$CHAIN_ID" "${KT[@]}")
b0=$(bal "$TO"); "$BIN" tx bank send alloc-community "$TO" "1$Z$D" -y --node "$(node 0)" "${TX[@]}" >/dev/null; sleep 8
check "transfer of 1 NODE via node0, seen on node3" "$(calc "$("$BIN" q bank balance "$TO" "$D" --node "$(node 3)" --output json | jq -r '.balance.amount // "0"') - $b0")" "1$Z"
# Double spend: two different transfers signed with the same sequence, sent to two different validators. Only one may execute.
s=$(acct "$FROM" sequence); n=$(acct "$FROM" account_number); b1=$(bal "$TO")
for amt in 1 2; do "$BIN" tx bank send alloc-community "$TO" "$amt$Z$D" --generate-only "${TX[@]}" > "$NET/ds$amt.json"
 "$BIN" tx sign "$NET/ds$amt.json" --from alloc-community --offline --sequence "$s" --account-number "$n" "${TX[@]}" > "$NET/ds$amt.signed.json"; done
"$BIN" tx broadcast "$NET/ds1.signed.json" --node "$(node 0)" "${TX[@]}" >/dev/null 2>&1; "$BIN" tx broadcast "$NET/ds2.signed.json" --node "$(node 1)" "${TX[@]}" >/dev/null 2>&1; sleep 10
d=$(calc "$(bal "$TO") - $b1"); { [ "$d" = "1$Z" ] || [ "$d" = "2$Z" ]; } && ok "double spend: exactly one of the two transfers executed ($d)" || bad "double spend: recipient change $d"
check "sender sequence advanced by exactly 1" "$(acct "$FROM" sequence)" "$((s + 1))"
stop_node 3; a=$(height 0); sleep 15; [ "$(height 0)" -gt "$a" ] && ok "1 of 4 validators down: chain continues (75% > 2/3)" || bad "chain stopped with 3 of 4 validators"
stop_node 2; sleep 6; a=$(height 0); sleep 20; [ "$(height 0)" -le $((a + 1)) ] && ok "2 of 4 validators down: chain halts, no finality without 2/3" || bad "chain advanced without 2/3 voting power"
start_node 2; start_node 3; sleep 30; h0=$(height 0); h3=$(height 3)
[ "$h0" -gt $((a + 1)) ] && ok "restarted validators: chain resumes (height $h0)" || bad "chain did not resume"
[ $((h0 - h3)) -le 2 ] && ok "crashed node3 caught up ($h3 vs $h0)" || bad "node3 behind: $h3 vs $h0"
check "total supply unchanged after all tests" "$(SUP)" "1000000000$Z"
[ "$FAIL" = 0 ] && log "RESULT: ALL CHECKS PASSED" || log "RESULT: FAILURES ABOVE"; log "Saved: $OUT"; exit "$FAIL"
#!/usr/bin/env bash
# Phase 3 checks on the running 4-validator localnet: no mint module, no mint account, supply fixed at every height.
# Run after: build-chain.sh, localnet-multi.sh init, localnet-multi.sh start
set -uo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"; source "$ROOT/blockchain/versions.env"; source "$ROOT/scripts/lib/multi.sh"
Z=000000000000000000; D="$BASE_DENOM"; KT=(--keyring-backend test --home "$NET/genesis-build"); FAIL=0
mkdir -p "$ROOT/tests/results"; OUT="$ROOT/tests/results/phase3-$(date -u +%Y%m%dT%H%M%SZ).txt"
log(){ echo "$*" | tee -a "$OUT"; }; ok(){ log "PASS $1"; }; bad(){ log "FAIL $1"; FAIL=1; }
check(){ [ "$2" = "$3" ] && ok "$1 ($2)" || bad "$1: expected $3, got $2"; }
q(){ "$BIN" q "$@" --node "$(node 0)" --output json; }
SUP(){ q bank total-supply | jq -r ".supply[]|select(.denom==\"$D\").amount"; }
log "NODELINK phase 3 verification $(date -u)"; "$BIN" version >>"$OUT" 2>&1
[ -f "$ROOT/blockchain/nodelinkd/supply_guard.go" ] && ok "supply guard present in chain source" || bad "supply_guard.go missing"
if [ -n "${SKIP_UNIT_TESTS:-}" ]; then log "SKIP fixed supply unit tests (SKIP_UNIT_TESTS set)"; else
(cd "$ROOT/blockchain/nodelinkd" && go test -run TestFixedSupply -count=1 . >>"$OUT" 2>&1) && ok "fixed supply unit tests" || bad "fixed supply unit tests"; fi
"$BIN" q mint params --node "$(node 0)" >/dev/null 2>&1 && bad "mint query still answers" || ok "no mint module in the binary"
"$BIN" tx mint --help >/dev/null 2>&1 && "$BIN" tx mint --help 2>&1 | grep -qi "update" && bad "mint transactions exist" || ok "no mint transactions"
check "mint state in genesis" "$(jq -r '.app_state.mint // "none"' "$(nh 0)/config/genesis.json")" "none"
check "mint module account" "$(q auth module-accounts | jq '[..|objects|select(.name?=="mint")]|length')" "0"
for k in 1 2 3 4 5; do check "total supply, sample $k at height $(height 0)" "$(SUP)" "1000000000$Z"; sleep 3; done
TO=$("$BIN" keys show demo-recipient -a "${KT[@]}" 2>/dev/null || { "$BIN" keys add demo-recipient --algo eth_secp256k1 "${KT[@]}" >/dev/null 2>&1; "$BIN" keys show demo-recipient -a "${KT[@]}"; })
"$BIN" tx bank send alloc-community "$TO" "5$Z$D" -y --node "$(node 0)" --gas 200000 --gas-prices "2000000000$D" --chain-id "$CHAIN_ID" "${KT[@]}" >/dev/null; sleep 8
check "total supply after a transfer with fees" "$(SUP)" "1000000000$Z"
grep -l "fixed supply violated" "$NET"/logs/*.log >/dev/null 2>&1 && bad "a node logged a supply violation" || ok "no supply violation in any node log"
[ "$FAIL" = 0 ] && log "RESULT: ALL CHECKS PASSED" || log "RESULT: FAILURES ABOVE"; log "Saved: $OUT"; exit "$FAIL"
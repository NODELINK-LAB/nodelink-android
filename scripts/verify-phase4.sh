#!/usr/bin/env bash
# Phase 4 checks on the running 4-validator localnet: rewards from the reserve, supply fixed, downtime slashing and unjail.
# Run after: build-chain.sh, localnet-multi.sh init, localnet-multi.sh start. Takes about 5 minutes; stops and restarts node3.
set -uo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"; source "$ROOT/blockchain/versions.env"; source "$ROOT/scripts/lib/genesis.sh"; source "$ROOT/scripts/lib/multi.sh"
Z=000000000000000000; D="$BASE_DENOM"; FAIL=0; mkdir -p "$ROOT/tests/results"; OUT="$ROOT/tests/results/phase4-$(date -u +%Y%m%dT%H%M%SZ).log"
log(){ echo "$*" | tee -a "$OUT"; }; ok(){ log "PASS $1"; }; bad(){ log "FAIL $1"; FAIL=1; }
command -v bc >/dev/null || command -v python3 >/dev/null || { echo "Missing tool: bc or python3 (amounts exceed 64-bit integers)"; exit 1; }
# Exact big-integer maths; python3 when bc is absent (/ becomes integer division, comparisons print 1 or 0).
calc(){ if command -v bc >/dev/null; then echo "scale=0; $1" | BC_LINE_LENGTH=0 bc; else python3 -c "print(int(${1//\//\/\/}))"; fi; }
check(){ [ "$2" = "$3" ] && ok "$1 ($2)" || bad "$1: expected $3, got $2"; }
q(){ "$BIN" q "$@" --node "$(node 0)" --output json; }
SUP(){ q bank total-supply | jq -r ".supply[]|select(.denom==\"$D\").amount"; }
BAL(){ q bank balances "$1" "${@:2}" | jq -r "[.balances[]|select(.denom==\"$D\").amount]|first // \"0\""; }
VAL(){ q staking validator "$1" | jq -r "(.validator // .).$2 // false"; }
OUTST(){ q distribution validator-outstanding-rewards "$1" | jq -r "[..|strings|select(endswith(\"$D\"))]|first // \"0$D\"|rtrimstr(\"$D\")|split(\".\")[0]"; }   # this SDK prints coins as "123.45anode"
TX(){ "$BIN" tx "$@" -y --node "$(node 0)" --gas 300000 --gas-prices "2000000000$D" --chain-id "$CHAIN_ID" --keyring-backend test >/dev/null; sleep 6; }
log "NODELINK phase 4 verification $(date -u)"; "$BIN" version >>"$OUT" 2>&1
if [ -n "${SKIP_UNIT_TESTS:-}" ]; then log "SKIP reward and supply unit tests (SKIP_UNIT_TESTS set)"; else
(cd "$ROOT/blockchain/nodelinkd" && go test -run 'TestReward|TestFixedSupply' -count=1 . >>"$OUT" 2>&1) && ok "reward and supply unit tests" || bad "reward and supply unit tests"; fi

R=$(nodelink_reserve_addr); TOTAL=$(SUP)   # supply at start: 1,000,000,000 NODE on a fresh network, less any slashes burned in earlier runs
[ "$(calc "$TOTAL <= 1000000000$Z")" = 1 ] && ok "supply at start within the 1,000,000,000 NODE cap ($TOTAL)" || bad "supply above cap: $TOTAL"
check "reserve module account has no permissions" "$(q auth module-account rewardreserve | jq -c '(.account // .).permissions // []')" "[]"
check "nobody holds a key for the reserve" "$("$BIN" keys list --keyring-backend test --home "$NET/genesis-build" --output json | jq "[.[]|select(.address==\"$R\")]|length")" "0"
H=$(( $(height 0) - 2 )); B1=$(BAL "$R" --height "$H"); B2=$(BAL "$R" --height $((H + 1)))
check "reserve pays exactly remaining/63115200 per block (height $((H + 1)))" "$(calc "($B1 - $B2) == ($B1 / 63115200)")" "1"
[ "$(calc "$B1 < 200000000$Z")" = 1 ] && ok "reserve has paid out ($B1 left)" || bad "reserve did not pay"
V0=$("$BIN" keys show node0 --bech val -a --keyring-backend test --home "$(nh 0)"); A0=$("$BIN" keys show node0 -a --keyring-backend test --home "$(nh 0)")
O1=$(OUTST "$V0"); h1=$(height 0); for s in $(seq 1 20); do [ "$(height 0)" -ge $((h1 + 2)) ] && break; sleep 1; done; O2=$(OUTST "$V0")   # at least 2 new blocks
[ -n "$O1" ] && [ -n "$O2" ] && [ "$(calc "$O2 > $O1")" = 1 ] && ok "node0 rewards growing ($O1 -> $O2 anode, height $h1 -> $(height 0))" || bad "node0 rewards not growing ('$O1' -> '$O2', height $h1 -> $(height 0))"
W1=$(BAL "$A0"); TX distribution withdraw-rewards "$V0" --commission --from node0 --home "$(nh 0)"; W2=$(BAL "$A0")
[ "$(calc "$W2 > $W1")" = 1 ] && ok "rewards withdrawn to node0 wallet" || bad "withdraw did not raise balance ($W1 -> $W2)"
for k in 1 2 3; do check "total supply unchanged while rewards are paid, sample $k" "$(SUP)" "$TOTAL"; sleep 3; done

# Downtime: node3 offline more than half of a 50-block window gets jailed, 1% of its stake burned.
V3=$("$BIN" keys show node3 --bech val -a --keyring-backend test --home "$(nh 3)")
T3=$(VAL "$V3" tokens); S0=$(SUP); stop_node 3; log "node3 stopped at height $(height 0), waiting for jail…"
for s in $(seq 1 60); do [ "$(VAL "$V3" jailed)" = true ] && break; sleep 3; done
check "offline validator jailed" "$(VAL "$V3" jailed)" "true"
T3b=$(VAL "$V3" tokens); SL=$(calc "$T3 - $T3b")
check "1% of node3 stake slashed" "$(calc "$SL == ($T3 / 100)")" "1"
check "slashed NODE burned: supply fell by exactly the slash" "$(calc "($S0 - $(SUP)) == $SL")" "1"
[ "$(height 0)" -gt 0 ] && ok "chain still running with node3 jailed (height $(height 0))" || bad "chain stopped"
start_node 3; log "node3 restarted, waiting out the 60s jail…"; sleep 70
TX slashing unjail --from node3 --home "$(nh 3)"
check "node3 unjailed after jail time" "$(VAL "$V3" jailed)" "false"
grep -l "fixed supply violated" "$NET"/logs/*.log >/dev/null 2>&1 && bad "a node logged a supply violation" || ok "no supply violation in any node log"
log "Not tested here: double signing (5% burn + permanent ban). It needs two nodes signing with one key; see docs/PHASE4.md."
[ "$FAIL" = 0 ] && log "RESULT: ALL CHECKS PASSED" || log "RESULT: FAILURES ABOVE"; log "Saved: $OUT"; exit "$FAIL"
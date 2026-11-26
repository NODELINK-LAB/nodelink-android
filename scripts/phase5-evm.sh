#!/usr/bin/env bash
# Phase 5: makes the in-process test network usable and runs the full NODELINK chain test (phases 1 to 5):
# four real validators in one process, NODELINK genesis, Ethereum JSON-RPC, transfers, contract deploy, slashing.
# Needs only Go: no separate nodes, ports or background processes. Takes about 3 minutes. Safe to rerun.
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"; source "$ROOT/blockchain/versions.env"
APP="$ROOT/blockchain/nodelinkd"; P="$ROOT/blockchain/patches/phase5"
[ -f "$APP/PHASE4_APPLIED" ] || bash "$ROOT/scripts/build-chain.sh"
cd "$APP"
# Upstream test network replaced the whole bank genesis, dropping denom metadata (EVM start fails) and extra balances.
perl -0pi -e 's#\tvar bankGenState banktypes\.GenesisState\n\tbankGenState\.Balances = genBalances\n#\tvar bankGenState banktypes.GenesisState\n\tcfg.Codec.MustUnmarshalJSON(cfg.GenesisState[banktypes.ModuleName], &bankGenState)\n\tbankGenState.Balances = append(bankGenState.Balances, genBalances...)\n\tbankGenState.Supply = nil\n#' tests/network/util.go
grep -q 'append(bankGenState.Balances, genBalances...)' tests/network/util.go || { echo "Test network bank patch failed"; exit 1; }
cp "$P/test_ports.go" tests/network/test_ports.go; cp "$P/nodelink_chain_test.go" tests/network/nodelink_chain_test.go
go vet ./tests/network/
mkdir -p "$ROOT/tests/results"; OUT="$ROOT/tests/results/chain-$(date -u +%Y%m%dT%H%M%SZ).log"
if go test ./tests/network/ -run TestNodelinkChain -count=1 -v -timeout 10m > "$OUT" 2>&1; then R="ALL CHECKS PASSED"; else R="FAILED"; fi
grep -E '^\s+nodelink_chain_test.go:[0-9]+: PASS|--- (PASS|FAIL)|Error:|expected|actual' "$OUT" || true
echo "RESULT: $R (log $OUT)"; [ "$R" = "ALL CHECKS PASSED" ]
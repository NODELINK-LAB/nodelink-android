#!/usr/bin/env bash
# Phase 6 on-chain check without Foundry: compiles the contracts with solc 0.8.24 and runs them on the
# running 4-validator localnet with real signed transactions (tests/phase6/main.go in nodelinkd).
# Run after: build-chain.sh, localnet-multi.sh init, localnet-multi.sh start. Takes about 2 minutes.
set -uo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"; source "$ROOT/blockchain/versions.env"; source "$ROOT/scripts/lib/multi.sh"
mkdir -p "$ROOT/tests/results"; OUT="$ROOT/tests/results/phase6-onchain-$(date -u +%Y%m%dT%H%M%SZ).log"
SOLC="${SOLC:-$(command -v solc || echo "$ROOT/blockchain/bin/solc")}"
if [ ! -x "$SOLC" ]; then curl -sfL -o "$ROOT/blockchain/bin/solc" https://binaries.soliditylang.org/linux-amd64/solc-linux-amd64-v0.8.24+commit.e11b9ed9 && chmod +x "$ROOT/blockchain/bin/solc"; SOLC="$ROOT/blockchain/bin/solc"; fi
"$SOLC" --version | tail -1 | tee "$OUT"
"$SOLC" --evm-version shanghai --optimize --optimize-runs 200 --bin --abi -o "$ROOT/contracts/out-solc" --overwrite "$ROOT"/contracts/src/*.sol >>"$OUT" 2>&1 && echo "PASS contracts compile (solc 0.8.24)" | tee -a "$OUT" || { echo "FAIL contracts do not compile" | tee -a "$OUT"; exit 1; }
mkdir -p "$ROOT/blockchain/nodelinkd/tests/phase6"; cp "$ROOT/contracts/onchain/main.go" "$ROOT/blockchain/nodelinkd/tests/phase6/main.go"
(cd "$ROOT/blockchain/nodelinkd" && go build -o "$ROOT/blockchain/bin/phase6-check" ./tests/phase6) || { echo "FAIL check program does not build"; exit 1; }
sup(){ "$BIN" q bank total-supply --node "$(node 0)" --output json | jq -r ".supply[]|select(.denom==\"$BASE_DENOM\").amount"; }
S0=$(sup); KEY=$(echo y | "$BIN" keys unsafe-export-eth-key alloc-community --keyring-backend test --home "$NET/genesis-build" 2>&1 | tail -1)   # local test key only
RPC="http://localhost:$(evmport 0)" KEY="$KEY" OUT="$ROOT/contracts/out-solc" "$ROOT/blockchain/bin/phase6-check" 2>&1 | tee -a "$OUT"; R=${PIPESTATUS[0]}
[ "$(sup)" = "$S0" ] && echo "PASS contracts created no NODE: total supply unchanged ($S0)" | tee -a "$OUT" || { echo "FAIL total supply changed" | tee -a "$OUT"; R=1; }
[ "$R" = 0 ] && echo "RESULT: ALL CHECKS PASSED" | tee -a "$OUT" || echo "RESULT: FAILURES ABOVE" | tee -a "$OUT"; echo "Saved: $OUT"; exit "$R"
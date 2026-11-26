#!/usr/bin/env bash
# Builds blockchain/bin/nodelinkd from NODELINK's own chain module (blockchain/nodelinkd).
# Needs: Go (exact version in versions.env), git, gcc, jq. Linux or macOS.
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"; source "$ROOT/blockchain/versions.env"
for t in go gcc jq; do command -v "$t" >/dev/null || { echo "Missing tool: $t"; exit 1; }; done
go version | grep -q "go$GO_VERSION " || { echo "Need Go $GO_VERSION, found: $(go version)"; exit 1; }
[ -f "$ROOT/blockchain/nodelinkd/go.mod" ] || bash "$ROOT/scripts/vendor-chain.sh"
[ -f "$ROOT/blockchain/nodelinkd/PHASE3_APPLIED" ] || bash "$ROOT/scripts/phase3-fixed-supply.sh"
[ -f "$ROOT/blockchain/nodelinkd/PHASE4_APPLIED" ] || bash "$ROOT/scripts/phase4-staking-rewards.sh"
mkdir -p "$ROOT/blockchain/bin"
(cd "$ROOT/blockchain/nodelinkd" && CGO_ENABLED=1 go build -mod=readonly -trimpath -tags netgo -o "$ROOT/blockchain/bin/nodelinkd" ./cmd/nodelinkd)
echo "Built: $ROOT/blockchain/bin/nodelinkd"; "$ROOT/blockchain/bin/nodelinkd" version --long | head -8
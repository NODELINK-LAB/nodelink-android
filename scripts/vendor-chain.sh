#!/usr/bin/env bash
# Phase 2, one time: turns the pinned Cosmos EVM example chain into NODELINK's own Go module at blockchain/nodelinkd.
# After it succeeds, blockchain/nodelinkd (with go.mod and go.sum) is NODELINK source code: commit it and edit it there.
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"; source "$ROOT/blockchain/versions.env"
DEST="$ROOT/blockchain/nodelinkd"; MOD="github.com/nodelink/nodelink/blockchain/nodelinkd"
[ -e "$DEST/go.mod" ] && { echo "blockchain/nodelinkd already exists (NODELINK-owned code). Not touching it."; exit 0; }
for t in git go gcc; do command -v "$t" >/dev/null || { echo "Missing tool: $t"; exit 1; }; done
go version | grep -q "go$GO_VERSION " || { echo "Need Go $GO_VERSION, found: $(go version)"; exit 1; }
TMP="$(mktemp -d)"; trap 'rm -rf "$TMP"' EXIT
git clone --quiet https://github.com/cosmos/evm "$TMP/evm"; git -C "$TMP/evm" checkout --quiet "$COSMOS_EVM_COMMIT"
[ "$(git -C "$TMP/evm" rev-parse HEAD)" = "$COSMOS_EVM_COMMIT" ] || { echo "Source commit mismatch"; exit 1; }
cp -R "$TMP/evm/evmd" "$DEST"; cd "$DEST"
mv cmd/evmd cmd/nodelinkd
grep -rl --include='*.go' 'github.com/cosmos/evm/evmd' . | while read -r f; do sed -i.bak "s#github.com/cosmos/evm/evmd#$MOD#g; s#$MOD/cmd/evmd#$MOD/cmd/nodelinkd#g" "$f"; done
! grep -rq --include='*.go' "$MOD/cmd/evmd" . || { echo "Import path patch failed"; exit 1; }
sed -i.bak "s#^module github.com/cosmos/evm/evmd#module $MOD#" go.mod
go mod edit -dropreplace github.com/cosmos/evm -require "github.com/cosmos/evm@$COSMOS_EVM_VERSION"
sed -i.bak 's/Bech32Prefix = "cosmos"/Bech32Prefix = "'"$BECH32_PREFIX"'"/' config/bech32.go
grep -q "Bech32Prefix = \"$BECH32_PREFIX\"" config/bech32.go || { echo "Prefix patch failed"; exit 1; }
grep -rl --include='*.go' '"\.evmd"' . | while read -r f; do sed -i.bak 's/"\.evmd"/".nodelinkd"/g' "$f"; done
find . -name '*.bak' -delete
printf 'Derived from github.com/cosmos/evm %s (commit %s), evmd example chain, Apache-2.0.\nChanges: module path, binary name nodelinkd, Bech32 prefix %s, default home .nodelinkd.\n' "$COSMOS_EVM_VERSION" "$COSMOS_EVM_COMMIT" "$BECH32_PREFIX" > ORIGIN.md
cp "$TMP/evm/LICENSE" LICENSE
go mod tidy
go build ./...
echo "Done. NODELINK chain source: blockchain/nodelinkd. Commit go.mod and go.sum so later builds are reproducible."
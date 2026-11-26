#!/usr/bin/env bash
# Phase 4, one time: staking rewards paid per block from the rewardreserve module account (no minting).
# Runs automatically from build-chain.sh after phase 3. Done once PHASE4_APPLIED exists.
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"; source "$ROOT/blockchain/versions.env"
APP="$ROOT/blockchain/nodelinkd"; P="$ROOT/blockchain/patches/phase4"
[ -f "$APP/PHASE3_APPLIED" ] || { echo "Run scripts/phase3-fixed-supply.sh first"; exit 1; }
[ -f "$APP/PHASE4_APPLIED" ] && { echo "Phase 4 already applied."; exit 0; }
command -v perl >/dev/null || { echo "Missing tool: perl"; exit 1; }
cd "$APP"
e(){ perl -0pi -e "$2" "$1"; }

# Reserve module account with no permissions: it can hold and send NODE, never mint, burn or stake it.
grep -q '"rewardreserve"' config/permissions.go || e config/permissions.go 's#(\tgovtypes\.ModuleName: +\{authtypes\.Burner\},\n)#$1\t"rewardreserve": nil, // NODELINK staking reward reserve, see reward_reserve.go\n#'
# Pay before distribution's BeginBlocker, inside the supply check.
grep -q 'app.payStakingReward(ctx)' app.go || e app.go 's#\tapp\.beginSupplyCheck\(ctx\)\n\treturn app\.ModuleManager\.BeginBlock\(ctx\)#\tapp.beginSupplyCheck(ctx)\n\tif err := app.payStakingReward(ctx); err != nil {\n\t\treturn sdk.BeginBlock{}, err\n\t}\n\treturn app.ModuleManager.BeginBlock(ctx)#'
grep -q '"rewardreserve": nil' config/permissions.go && grep -q 'app.payStakingReward(ctx)' app.go || { echo "Reward wiring failed"; exit 1; }

cp "$P/reward_reserve.go" reward_reserve.go; cp "$P/reward_reserve_test.go" reward_reserve_test.go
gofmt -w config/permissions.go app.go
go build ./...
go vet . ./config/...
go test -run 'TestFixedSupply|TestReward' -count=1 .
echo "Staking reward reserve installed by scripts/phase4-staking-rewards.sh" > PHASE4_APPLIED
echo "Phase 4 applied: staking rewards from the reserve, no minting. Commit blockchain/nodelinkd."
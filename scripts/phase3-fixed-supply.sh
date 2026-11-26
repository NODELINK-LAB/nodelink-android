#!/usr/bin/env bash
# Phase 3, one time: removes x/mint from blockchain/nodelinkd and installs the per-block fixed supply check.
# Runs automatically from build-chain.sh. Safe to rerun: edits that already landed match nothing; done once PHASE3_APPLIED exists.
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"; source "$ROOT/blockchain/versions.env"
APP="$ROOT/blockchain/nodelinkd"; P="$ROOT/blockchain/patches/phase3"
[ -f "$APP/go.mod" ] || { echo "Run scripts/vendor-chain.sh first"; exit 1; }
[ -f "$APP/PHASE3_APPLIED" ] && { echo "Phase 3 already applied."; exit 0; }
command -v perl >/dev/null || { echo "Missing tool: perl"; exit 1; }
cd "$APP"
e(){ perl -0pi -e "$2" "$1"; }

# app.go: no mint store, keeper, module, genesis, ordering or getter; supply check around every block.
e app.go 's#\t"github.com/cosmos/cosmos-sdk/x/mint"\n\tmintkeeper "github.com/cosmos/cosmos-sdk/x/mint/keeper"\n\tminttypes "github.com/cosmos/cosmos-sdk/x/mint/types"\n##'
e app.go 's#\tMintKeeper +mintkeeper\.Keeper\n##'
e app.go 's#minttypes\.StoreKey, distrtypes\.StoreKey,#distrtypes.StoreKey,#'
e app.go 's#\tapp\.MintKeeper = mintkeeper\.NewKeeper\(.*?\n\t\)\n\n##s'
e app.go 's#\t\tmint\.NewAppModule\(appCodec, app\.MintKeeper, app\.AccountKeeper, nil, nil\),\n##'
e app.go 's#slashingtypes\.ModuleName, minttypes\.ModuleName,#slashingtypes.ModuleName,#'
e app.go 's#\t\tminttypes\.ModuleName,\n##g'
e app.go 's#\tmintGenState := NewMintGenesisState\(\)\n\tgenesis\[minttypes\.ModuleName\] = app\.appCodec\.MustMarshalJSON\(mintGenState\)\n\n##'
e app.go 's#func \(app \*EVMD\) GetMintKeeper\(\) mintkeeper\.Keeper \{\n\treturn app\.MintKeeper\n\}\n\n##'
grep -q 'supply supplyGuard' app.go || e app.go 's#\tconfigurator module\.Configurator\n#\tconfigurator module.Configurator\n\n\t// NODELINK fixed supply check, see supply_guard.go\n\tsupply supplyGuard\n#'
grep -q 'app.beginSupplyCheck(ctx)' app.go || e app.go 's#\treturn app\.ModuleManager\.BeginBlock\(ctx\)#\tapp.beginSupplyCheck(ctx)\n\treturn app.ModuleManager.BeginBlock(ctx)#'
e app.go 's#\treturn app\.ModuleManager\.EndBlock\(ctx\)#\tres, err := app.ModuleManager.EndBlock(ctx)\n\tif err != nil {\n\t\treturn res, err\n\t}\n\treturn res, app.endSupplyCheck(ctx)#'

# No module account may hold the Minter permission for mint.
e config/permissions.go 's#\tminttypes "github.com/cosmos/cosmos-sdk/x/mint/types"\n##; s#\tminttypes\.ModuleName: +\{authtypes\.Minter\},\n##'
e genesis.go 's#\n\tminttypes "github.com/cosmos/cosmos-sdk/x/mint/types"\n##; s#// NewMintGenesisState.*?\n\}\n\n##s'
# Upstream test helpers that set up mint genesis; one IBC test mints through the mint account and is dropped.
e tests/network/util.go 's#\tminttypes "github.com/cosmos/cosmos-sdk/x/mint/types"\n##; s#\tvar inflationGenState minttypes\.GenesisState\n.*?MustMarshalJSON\(&inflationGenState\)\n##s'
e tests/integration/create_app.go 's#\tminttypes "github.com/cosmos/cosmos-sdk/x/mint/types"\n##; s#\tmintGen := minttypes\.DefaultGenesisState\(\)\n.*?MustMarshalJSON\(mintGen\)\n##s'
rm -f tests/ibc/ics20_recursive_precompile_calls_test.go

# Every edit must have landed; otherwise stop before building something half-patched.
left="$(grep -rln --include='*.go' -e 'x/mint' -e 'MintKeeper' . | grep -v '^\./mint_stub\.go$' || true)"
[ -z "$left" ] || { echo "Mint references left in: $left"; exit 1; }
grep -q 'app.beginSupplyCheck(ctx)' app.go && grep -q 'app.endSupplyCheck(ctx)' app.go && grep -q 'supply supplyGuard' app.go || { echo "Supply check wiring failed"; exit 1; }

cp "$P/mint_stub.go" mint_stub.go; cp "$P/supply_guard_test.go" supply_guard_test.go; cp "$P/supply_guard.go" supply_guard.go

go mod tidy
go build ./...
go vet . ./config/...
go test -run 'TestFixedSupply' -count=1 .
echo "x/mint removed and fixed supply check installed by scripts/phase3-fixed-supply.sh" > PHASE3_APPLIED
echo "Phase 3 applied: x/mint removed, fixed supply check active. Commit blockchain/nodelinkd."
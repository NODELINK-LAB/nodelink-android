package evmd

// NODELINK fixed supply rule (phase 3).
// All NODE is created at genesis. The mint module is removed from the app, and every block is checked:
// total anode supply may never exceed the cap and may never be higher at the end of a block than at its start.
// A violation makes FinalizeBlock fail, so every honest validator halts instead of committing new coins.

import (
	"fmt"

	sdkmath "cosmossdk.io/math"

	sdk "github.com/cosmos/cosmos-sdk/types"
)

const (
	// FixedSupplyDenom is the base unit of NODE (1 NODE = 10^18 anode).
	FixedSupplyDenom = "anode"
	// fixedSupplyAnode is 1,000,000,000 NODE in anode.
	fixedSupplyAnode = "1000000000000000000000000000"
)

// FixedSupplyCap returns the maximum total supply in anode.
func FixedSupplyCap() sdkmath.Int {
	v, ok := sdkmath.NewIntFromString(fixedSupplyAnode)
	if !ok {
		panic("invalid NODELINK supply cap")
	}
	return v
}

// CheckSupply is the pure rule: after must not exceed the cap or the supply at the start of the block.
// Burns (lower supply) are allowed.
func CheckSupply(before, after sdkmath.Int) error {
	if after.GT(FixedSupplyCap()) {
		return fmt.Errorf("NODELINK fixed supply violated: %s%s exceeds cap %s%s", after, FixedSupplyDenom, FixedSupplyCap(), FixedSupplyDenom)
	}
	if after.GT(before) {
		return fmt.Errorf("NODELINK fixed supply violated: block raised supply from %s to %s%s", before, after, FixedSupplyDenom)
	}
	return nil
}

type supplyGuard struct {
	atBegin sdkmath.Int
	set     bool
}

func (app *EVMD) supplyNow(ctx sdk.Context) sdkmath.Int {
	return app.BankKeeper.GetSupply(ctx, FixedSupplyDenom).Amount
}

func (app *EVMD) beginSupplyCheck(ctx sdk.Context) {
	app.supply = supplyGuard{atBegin: app.supplyNow(ctx), set: true}
}

func (app *EVMD) endSupplyCheck(ctx sdk.Context) error {
	now := app.supplyNow(ctx)
	before := now
	if app.supply.set {
		before = app.supply.atBegin
	}
	app.supply.set = false
	return CheckSupply(before, now)
}
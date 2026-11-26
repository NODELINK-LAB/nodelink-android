package evmd

import (
	mintkeeper "github.com/cosmos/cosmos-sdk/x/mint/keeper"
)

// GetMintKeeper exists only because the upstream Cosmos EVM test harness interface requires it.
// NODELINK has no mint module: no store, no module account, no messages. This returns an empty,
// unusable keeper; any test that tries to mint through it fails, which is the intended rule.
func (app *EVMD) GetMintKeeper() mintkeeper.Keeper {
	return mintkeeper.Keeper{}
}
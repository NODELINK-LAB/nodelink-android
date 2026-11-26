package evmd

// NODELINK staking rewards (phase 4).
// There is no minting, so rewards come only from the staking-rewards allocation (200,000,000 NODE), held at genesis
// by the rewardreserve module account. That account has no permissions and no private key: nobody can spend it.
// At the start of every block a fixed share of what is left moves to the fee collector, and x/distribution pays it,
// together with transaction fees, to validators and delegators by stake. This is a transfer, so supply is unchanged.

import (
	sdkmath "cosmossdk.io/math"

	sdk "github.com/cosmos/cosmos-sdk/types"
	authtypes "github.com/cosmos/cosmos-sdk/x/auth/types"
)

const (
	// RewardReserveName is the module account that holds the staking-rewards allocation.
	RewardReserveName = "rewardreserve"
	// RewardDivisor: each block pays remaining/63,115,200. With 5 second blocks (6,311,520 per year) that is about
	// 9.5% of the remaining reserve per year, decaying forever and never reaching zero or going negative.
	RewardDivisor = 63_115_200
)

// RewardPerBlock is the pure payout rule.
func RewardPerBlock(remaining sdkmath.Int) sdkmath.Int {
	if !remaining.IsPositive() {
		return sdkmath.ZeroInt()
	}
	return remaining.QuoRaw(RewardDivisor)
}

// RewardReserveAddress is the reserve's account address (same as authtypes.NewModuleAddress).
func RewardReserveAddress() sdk.AccAddress {
	return authtypes.NewModuleAddress(RewardReserveName)
}

// payStakingReward runs before the distribution BeginBlocker so the payment is shared out in the same block.
// A failing transfer fails the block rather than skipping a payment on some validators only.
func (app *EVMD) payStakingReward(ctx sdk.Context) error {
	left := app.BankKeeper.GetBalance(ctx, RewardReserveAddress(), FixedSupplyDenom).Amount
	pay := RewardPerBlock(left)
	if pay.IsZero() {
		return nil
	}
	return app.BankKeeper.SendCoinsFromModuleToModule(ctx, RewardReserveName, authtypes.FeeCollectorName,
		sdk.NewCoins(sdk.NewCoin(FixedSupplyDenom, pay)))
}
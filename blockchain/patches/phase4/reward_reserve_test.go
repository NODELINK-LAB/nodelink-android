package evmd

import (
	"testing"

	sdkmath "cosmossdk.io/math"

	authtypes "github.com/cosmos/cosmos-sdk/x/auth/types"

	"github.com/nodelink/nodelink/blockchain/nodelinkd/config"
)

func reserveStart() sdkmath.Int {
	v, _ := sdkmath.NewIntFromString("200000000000000000000000000") // 200,000,000 NODE
	return v
}

func TestRewardPerBlockValues(t *testing.T) {
	if !RewardPerBlock(sdkmath.ZeroInt()).IsZero() || !RewardPerBlock(sdkmath.NewInt(-5)).IsZero() {
		t.Fatal("empty or negative reserve must pay nothing")
	}
	if !RewardPerBlock(sdkmath.NewInt(RewardDivisor - 1)).IsZero() {
		t.Fatal("dust below the divisor must pay nothing")
	}
	// First block from 200M NODE: about 3.17 NODE, exactly floor(start / divisor).
	got, start := RewardPerBlock(reserveStart()), reserveStart()
	if got.MulRaw(RewardDivisor).GT(start) || got.AddRaw(1).MulRaw(RewardDivisor).LTE(start) {
		t.Fatalf("first block reward %s is not floor(%s / %d)", got, start, RewardDivisor)
	}
	low, _ := sdkmath.NewIntFromString("3160000000000000000")
	high, _ := sdkmath.NewIntFromString("3170000000000000000")
	if got.LT(low) || got.GT(high) {
		t.Fatalf("first block reward %s, want about 3.17 NODE", got)
	}
}

func TestRewardReserveNeverOverpays(t *testing.T) {
	left, paid := reserveStart(), sdkmath.ZeroInt()
	for i := 0; i < 100000; i++ {
		p := RewardPerBlock(left)
		if p.IsNegative() || p.GT(left) {
			t.Fatalf("block %d pays %s from %s", i, p, left)
		}
		left, paid = left.Sub(p), paid.Add(p)
	}
	if !left.Add(paid).Equal(reserveStart()) {
		t.Fatal("reserve plus paid out must equal the start: rewards are transfers, never new coins")
	}
	if !left.IsPositive() {
		t.Fatal("reserve must not run out")
	}
}

func TestRewardReserveAccountHasNoPermissions(t *testing.T) {
	perms, ok := config.GetMaccPerms()[RewardReserveName]
	if !ok || len(perms) != 0 {
		t.Fatalf("rewardreserve must be a module account with no mint, burn or staking permission, got %v %v", ok, perms)
	}
	if !RewardReserveAddress().Equals(authtypes.NewModuleAddress("rewardreserve")) {
		t.Fatal("reserve address mismatch")
	}
	if !config.BlockedAddresses()[RewardReserveAddress().String()] {
		t.Fatal("reserve must be blocked from receiving ordinary transfers")
	}
}
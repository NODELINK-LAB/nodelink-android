package evmd

import (
	"testing"

	sdkmath "cosmossdk.io/math"
)

func TestFixedSupplyCapIsOneBillionNode(t *testing.T) {
	want := sdkmath.NewInt(1_000_000_000).Mul(sdkmath.NewInt(1_000_000_000_000_000_000))
	if !FixedSupplyCap().Equal(want) {
		t.Fatalf("cap %s, want %s", FixedSupplyCap(), want)
	}
}

func TestFixedSupplyRule(t *testing.T) {
	capAmt := FixedSupplyCap()
	one := sdkmath.OneInt()
	cases := []struct {
		name          string
		before, after sdkmath.Int
		ok            bool
	}{
		{"unchanged at cap", capAmt, capAmt, true},
		{"burn lowers supply", capAmt, capAmt.Sub(one), true},
		{"one anode above cap", capAmt, capAmt.Add(one), false},
		{"mint after a burn, still under cap", capAmt.Sub(one.MulRaw(10)), capAmt.Sub(one), false},
		{"zero change below cap", capAmt.Sub(one), capAmt.Sub(one), true},
	}
	for _, c := range cases {
		err := CheckSupply(c.before, c.after)
		if (err == nil) != c.ok {
			t.Errorf("%s: ok=%v, err=%v", c.name, c.ok, err)
		}
	}
}
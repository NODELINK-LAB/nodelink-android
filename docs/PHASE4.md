# Phase 4 report: staking rewards and slashing

## Built
- blockchain/nodelinkd/reward_reserve.go: the 200,000,000 NODE staking-rewards allocation sits in the rewardreserve module account. It has no permissions (no mint, burn or staking) and no private key, so no person can spend it, and ordinary transfers to it are refused. At the start of every block remaining/63,115,200 moves to the fee collector, and x/distribution shares it with transaction fees among validators and delegators by stake (2% community tax, the SDK default, goes to the community pool). It is a transfer, so the phase 3 supply check still holds.
- Schedule: with 5 second blocks about 9.5% of what is left per year: about 19.0M NODE in year 1, 17.2M in year 2, 100M paid after about 6.9 years, never reaching zero. At a 30% staking ratio that is roughly 6% a year for stakers in year 1, plus fees. The local network uses 2 second blocks, so it pays 2.5 times faster.
- scripts/phase4-staking-rewards.sh (runs from build-chain.sh, once): adds the account, calls the payout before distribution in every block, builds and runs the tests.
- Genesis (scripts/lib/genesis.sh): the staking-rewards allocation now goes to the reserve instead of a keyed account. Slashing: downtime 1% burned and jail, double signing 5% burned and permanent ban. Local test window 50 blocks, 50% must be signed, 60 second jail; the public testnet genesis (phase 14) will use 10,000 blocks, 5%, 10 minutes.
- reward_reserve_test.go: payout value, never more than what is left, reserve plus paid equals the start over 100,000 blocks, account has no permissions and is blocked.
- scripts/verify-phase4.sh: on the 4-node network: account and key checks, exact per-block payout, rewards growing and withdrawable, supply unchanged, node3 jailed after downtime with exactly 1% burned and supply lower by that amount, chain still running, unjail after 60 seconds.

## Verified (2026-11-25, Linux, Go 1.25.9, cosmos/evm v0.7.4)
- phase4-staking-rewards.sh applied on top of phase 3, go build ./... and go vet passed, reward and fixed supply unit tests passed (ok, 0.150s).
- build-chain.sh produced nodelinkd with phase 4.

## Not verified
Genesis creation and the running network could not be run here. Run verify-phase4.sh on your machine. Double signing is not tested: it needs two nodes signing with one key; the rule is standard Cosmos SDK slashing.

## Reproduce
1. bash scripts/build-chain.sh
2. bash scripts/localnet-multi.sh init && bash scripts/localnet-multi.sh start
3. bash scripts/verify-phase3.sh, then bash scripts/verify-phase4.sh   (run phase 3 first: phase 4 burns 1% of node3's stake)
4. bash scripts/localnet-multi.sh stop

## Decisions for you
- The rate (9.5% of the remainder per year) is fixed in code; changing it needs a chain upgrade. Governance-adjustable rates need a full module (later phase).
- Slashing values for the public testnet are proposed above; confirm before phase 14.

## Risks
- If staking participation is very high, the yield per staker is low; if very low, high. Rewards are not targeted at a yield.
- Rewards come only from a finite reserve and fees. Long term, validators depend on fees.
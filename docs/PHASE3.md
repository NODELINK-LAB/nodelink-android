# Phase 3 report: fixed supply enforced by the chain

## Built
- scripts/phase3-fixed-supply.sh (runs automatically from build-chain.sh, once): removes x/mint from nodelinkd completely: store key, keeper, module, begin/end block and genesis order, default genesis, getter, and the mint module account with its Minter permission. Upstream test helpers that set up mint genesis are adjusted; one upstream IBC test that mints through the mint account is dropped. Every edit is checked and the script stops if a mint reference is left.
- blockchain/nodelinkd/supply_guard.go: consensus rule checked in every block. Total anode supply is read at the start and end of the block. End higher than start, or above 1,000,000,000 NODE (10^27 anode), makes the block fail, so all honest validators halt instead of committing new coins. Burns (slashing, burned deposits) are allowed and lower the supply for good.
- supply_guard_test.go: unit tests for the cap value and the rule (unchanged, burn, 1 anode over cap, re-mint after a burn).
- Genesis no longer carries any mint state.
- scripts/verify-phase3.sh: on the running 4-node network: guard present, unit tests, no mint query or transactions, no mint state or account, supply equal to 1,000,000,000 NODE at five heights and after a transfer with fees, no violation in any node log.
- Fixed a Phase 2 bug found while building: vendor-chain.sh renamed cmd/evmd but left its import path, so the module could not build. It now rewrites the path and checks it.

## Remaining Minter permissions, and why supply is still fixed
vm (EVM) and erc20 modules mint and burn internally to move balances and to wrap tokens; IBC transfer mints only foreign vouchers. Within a block their mints and burns of anode must cancel out, or the guard halts the chain. Governance cannot turn minting back on: there is no mint module to configure. Only a binary upgrade could, which every validator must install on purpose.

- mint_stub.go: the upstream test harness interface demands a GetMintKeeper method, so it returns an empty keeper with no store or module behind it.

## Verified (2026-11-25, Linux, Go 1.25.9, cosmos/evm v0.7.4)
- vendor-chain.sh: module created and go build ./... passed (after the Phase 2 import fix).
- phase3-fixed-supply.sh: all edits applied, no mint references left, go build ./... and go vet of the patched chain passed, fixed supply unit tests passed (ok, 0.086s).

## Not verified
The running chain was not started here. The 4-node network checks need a machine that can run four validators; run verify-phase3.sh there.

## Reproduce
1. bash scripts/build-chain.sh
2. bash scripts/localnet-multi.sh init && bash scripts/localnet-multi.sh start
3. bash scripts/verify-phase3.sh   (expected last line RESULT: ALL CHECKS PASSED)
4. bash scripts/localnet-multi.sh stop

## Risks
- A bug in vm or erc20 that nets a mint inside one block halts the chain (safe, but an outage) instead of silently inflating.
- A chain halt from the guard needs a fixed binary and coordinated restart.
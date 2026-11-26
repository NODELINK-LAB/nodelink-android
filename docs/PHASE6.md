# Phase 6 report: smart contracts

## Built (contracts/)
- src/WNODE.sol: Wrapped NODE, an ERC-20 backed 1:1 by native NODE held in the contract. Deposit (or a plain send) wraps, withdraw unwraps. It creates no NODE: WNODE supply always equals the NODE it holds. Needed by DEXs and other contracts that expect an ERC-20.
- src/NodeVesting.sol: locks NODE for one beneficiary, nothing before the cliff, then linear until the end. No owner, not revocable; anyone can trigger release, the NODE always goes to the beneficiary. Intended for core-team (100M) and other locked allocations: one contract per beneficiary.
- src/NodeMultisig.sol: M-of-N wallet for treasury NODE. An owner proposes a payment or call, it runs only after the threshold of owners confirm; confirmations can be revoked; a failing call stays pending. Owners and threshold are fixed at deployment.
- test/: Foundry unit tests for all three, including a fuzz test that WNODE is always fully backed, vesting at cliff, midway, end and after a late top-up, and multisig threshold, outsider, double-confirm, revoke and failed-call cases.
- script/Deploy.s.sol: deploys WNODE and a multisig (OWNERS, THRESHOLD from the environment).
- foundry.toml: solc 0.8.24, EVM version shanghai.
- scripts/phase6-contracts.sh: compiles, runs the unit tests, then deploys to the running 4-validator localnet and checks on chain: code present, wrap 5 NODE, unwrap 2 with the contract still fully backed, multisig pays 1 NODE, total NODE supply unchanged.

- contracts/onchain/main.go and scripts/verify-phase6.sh: a check that needs no Foundry. It compiles with solc 0.8.24 and, on the running localnet, deploys all three contracts with real signed transactions: wrap, unwrap, transfer, over-withdraw refused, contract always fully backed; multisig 2 of 3 refuses 1 confirmation, outsiders and double confirms, pays after 2, cannot pay twice; vesting releases nothing before a 20 s cliff, part after, everything after 60 s; total NODE supply unchanged.

## Verified
- 2026-11-25 17:37 UTC, in this workspace: all three contracts COMPILE with solc 0.8.24 (EVM shanghai, optimizer 200), no errors and no warnings. The on-chain check program also builds against the chain's own go-ethereum.
- Not yet run: the on-chain check (the workspace refused to start the validator network this time) and the Foundry unit tests (Foundry cannot be installed here).

## Not covered
- No independent audit. Do not hold real value in these contracts before one (phase 13).
- Stablecoin (phase 10), airdrop and presale contracts (phase 11, disabled) are not part of this phase.
- Vesting is per beneficiary and fixed; the multisig cannot change owners (deploy a new one and move funds).
- NODE sent to a contract through the Cosmos bank module (not the EVM) is not seen by receive(); WNODE ignores it and it cannot be withdrawn. Always wrap through the EVM.

## Reproduce (any machine with Foundry, Go 1.25 and the phase 2 tools)
bash scripts/build-chain.sh && bash scripts/localnet-multi.sh init && bash scripts/localnet-multi.sh start
bash scripts/verify-phase6.sh   (no Foundry needed; expected last line RESULT: ALL CHECKS PASSED)
bash scripts/phase6-contracts.sh   (Foundry; expected last line RESULT: ALL CHECKS PASSED; ONCHAIN=0 for compile and unit tests only)
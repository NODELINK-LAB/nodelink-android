# Phase 1 report

## Done
- Verified current versions from the Go module proxy and pinned them (blockchain/versions.env).
- Architecture, lifecycle, state, consensus, EVM, tokenomics and security design (docs/ARCHITECTURE.md).
- Repository structure created; empty areas say which phase starts them.
- scripts/build-chain.sh: builds nodelinkd from the exact Cosmos EVM commit, Bech32 prefix ndl.
- scripts/localnet-single.sh: chain id nodelink-1, EVM chain id 778001, base unit anode, shown as NODE (18 decimals, 1 NODE = 10^18 anode), zero inflation, exactly 1,000,000,000 NODE across the 10 allocations, one validator self-bonded with 1,000,000 NODE from genesis-validators.
- scripts/verify-phase1.sh: checks blocks advance, a real 1 NODE bank transfer succeeds, total supply stays 1,000,000,000 NODE, eth_chainId, eth_blockNumber and eth_getBalance against the bank balance.

## Not done, honestly
- Not built or run. The development workspace forbids installing Go or downloading source, so no block has been produced and NO test result exists. Run the commands below and send the saved results file back.
- nodelinkd is the upstream example chain with a new prefix and genesis, not yet a NODELINK-owned Go module (Phase 2).
- Minting is disabled by parameters only (Phase 3 makes it impossible).
- Staking rewards from the reserve, vesting and locks are not built; allocation accounts are plain accounts on this localnet.

## Reproduce (Linux or macOS, about 4 GB free disk, 8 GB RAM recommended)
1. Install Go 1.25.9 (go.dev/dl), git, gcc, make, jq, bc.
2. bash scripts/build-chain.sh   (first build downloads dependencies, can take 10-30 minutes)
3. bash scripts/localnet-single.sh   (leave it running)
4. In a second terminal: bash scripts/verify-phase1.sh
5. Expected last line: RESULT: ALL CHECKS PASSED. The file in tests/results/ is the record.
If a step fails, keep the full error output; the script versions are pinned so errors are reproducible.

## Next gate
Only after verify-phase1 passes on a real machine: Phase 2, a NODELINK Go module and a 4-validator Docker localnet with stop-one/stop-two tests.
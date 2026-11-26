# Phase 2 report: NODELINK chain module and 4-validator localnet

## Built
- scripts/vendor-chain.sh (run once, automatically by build-chain.sh): copies the pinned Cosmos EVM example chain into blockchain/nodelinkd as NODELINK's own Go module: module path github.com/nodelink/nodelink/blockchain/nodelinkd, binary cmd/nodelinkd, Bech32 prefix ndl, home .nodelinkd, Apache-2.0 licence and ORIGIN.md kept. go mod tidy pins go.sum; commit both. From then on all chain changes happen in blockchain/nodelinkd.
- scripts/lib/genesis.sh: one set of genesis rules (anode shown as NODE, zero inflation, EVM, precompiles) shared by the 1- and 4-node networks.
- scripts/localnet-multi.sh: 4 validators, separate homes, keys, ports and processes on one machine. Genesis-validators 100,000,000 NODE split 25,000,000 per validator, each self-bonds 1,000,000 NODE, so equal voting power. Other 9 allocations as before. Total still exactly 1,000,000,000 NODE.
- scripts/verify-phase2.sh: real checks against the running network: all nodes producing and agreeing on the same block, 4 bonded validators, eth_chainId on all 4, transfer sent to node0 visible on node3, double spend (two transfers with one sequence to two validators: exactly one executes), 1 validator down continues, 2 down halts, restart resumes and the crashed node catches up, supply unchanged.

## Not verified
Nothing was compiled or run: this workspace cannot install Go or download source. No result exists until you run it. Docker images are moved to phase 9 (infrastructure); this phase uses separate processes, which is enough for the consensus tests.

## Reproduce
1. bash scripts/build-chain.sh   (first run also creates blockchain/nodelinkd)
2. bash scripts/localnet-multi.sh init
3. bash scripts/localnet-multi.sh start
4. bash scripts/verify-phase2.sh   (about 2 minutes; expected last line RESULT: ALL CHECKS PASSED)
5. bash scripts/localnet-multi.sh stop
Ports: node i uses CometBFT RPC 26657+i, P2P 16656+i, REST 1317+i, gRPC 9090+i, EVM JSON-RPC 8545+10i. Logs in .localnet4/logs.

## Risks
- If the chain's testnet tool changes its port layout, rpcport reads the real value from each config, but EVM ports assume 8545+10i.
- All 4 validators on one machine prove the software, not independence or real network conditions; those come in phase 9 and the testnet.
- Minting is still disabled only by parameters (phase 3).
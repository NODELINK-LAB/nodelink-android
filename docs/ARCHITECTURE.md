# NODELINK Layer-1 architecture (Phase 1)

Status: design plus a buildable one-validator local prototype. Nothing has been compiled or run yet; see docs/PHASE1.md.

## Dependency selection (verified 2026-11-25 from proxy.golang.org)
| Component | Version | Why |
|---|---|---|
| Go | 1.25.9 | required by cosmos/evm v0.7.4 go.mod |
| Cosmos EVM | v0.7.4 (commit ba66d310) | latest release; EVM module, JSON-RPC, precompiles, ERC-20 module |
| Cosmos SDK | v0.54.3 | the version cosmos/evm v0.7.4 is built and tested against |
| CometBFT | v0.39.3 | same; CometBFT v1.0 is not yet supported by Cosmos EVM |
| go-ethereum | cosmos fork v1.17.2-cosmos-0 | replace directive in cosmos/evm |
Newer SDK v0.55 / CometBFT v1.0 exist; upgrading them separately would break compatibility. Upgrade only as a tested release family.

## Layers
```
 Android wallet (Kotlin)   Web wallet / explorer (TypeScript)   MetaMask / Foundry
        |  Cosmos gRPC/REST :1317/:9090        |  EVM JSON-RPC :8545
 +----------------------------------------------------------------------+
 | nodelinkd  (Cosmos SDK app)                                           |
 |  ante: signature, nonce/sequence, fee  -> mempool                     |
 |  modules: auth bank staking distribution slashing gov evidence        |
 |           vm(EVM) feemarket erc20 precisebank   [mint: removed]       |
 |  NODELINK modules (to build): supplycap, rewardreserve, vesting/claims|
 |  store: IAVL on LevelDB/PebbleDB, app hash per block                  |
 +------------------------------- ABCI++ --------------------------------+
 | CometBFT: BFT PoS consensus, >2/3 voting power commits, P2P :26656    |
 +----------------------------------------------------------------------+
 Mesh (phones, Wi-Fi Direct) stays off-chain; chain only records relay registry/rewards later.
```

## Transaction lifecycle
1. Wallet builds a Cosmos tx (eth_secp256k1 key, sign mode direct) or an Ethereum tx (EIP-155, chain id 778001).
2. CheckTx: ante handler verifies signature, account sequence/nonce, fee >= fee market base fee; invalid tx never enters mempool.
3. Proposer (rotating by voting power) builds a block; validators prevote/precommit; >2/3 voting power commits. Finality is instant; no forks after commit.
4. FinalizeBlock executes txs in order; state changes are written to the IAVL store; app hash goes in the next header.
5. Events and receipts are indexed for RPC, explorer and wallets.

## State
Accounts (auth), balances in anode (bank), validators/delegations/unbonding (staking), rewards (distribution), EVM code/storage (vm), ERC-20 token pairs (erc20). One coin: anode is the bank denom and the EVM gas coin, 18 decimals, so 1 NODE = 10^18 anode and EVM wei = anode.

## Consensus configuration (initial, to be tuned in Phase 9)
Block time ~2 s (local), max gas per block 10,000,000, unbonding 21 days on testnet/mainnet, slashing: double sign 5% + tombstone, downtime 0.01% with jail. Max validators 100 at start.

## Tokenomics enforcement design
- Total supply is created only at genesis: exactly 1,000,000,000 NODE in 10 module/allocation accounts (blockchain/genesis/allocations.json, script refuses another total).
- Phase 1: x/mint inflation set to zero. This is NOT a hard guarantee: governance could change mint params.
- Phase 3 (done in source, see docs/PHASE3.md): x/mint removed from the app; every block fails if total anode rises during the block or exceeds 10^27.
- Phase 4 (done in source, see docs/PHASE4.md): the rewardreserve module account (no permissions, no key) pays remaining/63,115,200 per block to stakers; it decays and never goes below zero. Fees go to validators (not burned), so supply stays constant.
- Team, presale, airdrop, treasury: on-chain vesting/claims or multisig; presale and airdrop transfers stay disabled until legal approval.

## Security boundaries
Validator consensus keys: hardware (HSM/tmkms) on testnet+. Node RPC behind a reverse proxy with rate limits; validators expose only P2P. No secrets in the repo; the localnet test keyring is for development only. Phones never send seeds anywhere. Independent audits before mainnet.

## Repository layout and mapping
blockchain/ (versions, genesis, built binary) · scripts/ · docs/ · tests/ · contracts/ · wallet/android/ · wallet/web/ · explorer/ · infrastructure/ · security/. The existing Android app (android-app/, identity-core/, network-core/) is the mesh app and will become wallet/android in Phase 7. ledger-core/ and validator-node/ are the earlier experimental Kotlin ledger, superseded by nodelinkd and kept only for reference.

## Phase plan
1 architecture + one-validator prototype (this) · 2 own Go module nodelinkd (fork of evmd with NODELINK app wiring), 4-validator localnet in Docker · 3 supply cap module + invariant tests · 4 staking rewards from reserve, slashing tests · 5 EVM with Foundry/MetaMask tests · 6 contracts · 7 wallets · 8 Blockscout · 9 infrastructure · 10 stablecoin design · 11 airdrop/presale (disabled) · 12 mesh integration · 13 security testing · 14 testnet, then mainnet only after audit.

## Open risks and decisions
- EVM chain id 778001 is provisional; check chainlist.org and ethereum-lists/chains before any public network.
- Base unit is anode (atto = 10^-18) so the name matches 18 decimals. Users only ever see NODE.
- One validator has no fault tolerance; it only proves the software runs.
- Issuing, selling or airdropping a coin may be restricted in Bangladesh (Bangladesh Bank has warned against virtual currencies). Legal advice needed before any public distribution.
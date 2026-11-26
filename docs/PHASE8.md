# Phase 8 report: block explorer

## Built
1. Quick explorer in the NODELINK web app at /explorer. Reads a validator's EVM JSON-RPC through the app's server (read only, no keys): checks the RPC is chain 778001, shows the latest 10 blocks, and searches by block number, transaction hash or 0x address (balance, transactions sent, contract code size). Transactions show status, value, fee, sender, recipient and created contract. It needs an RPC reachable from the internet (phase 9).
2. Full explorer, Blockscout, for a computer with Docker: explorer/docker-compose.yml (Postgres, Blockscout indexer and API, web frontend, all set to NODELINK, NODE, 18 decimals, chain 778001, testnet label) and scripts/explorer.sh. The script restarts node0 with the debug JSON-RPC namespace so internal transactions are indexed, generates a private SECRET_KEY_BASE once (explorer/.secret_key_base, not committed), and starts everything. Blockscout gives full address history, token (WNODE) pages and contract verification later.
3. scripts/lib/multi.sh: start_node accepts JSONRPC_API to choose JSON-RPC namespaces (default unchanged: eth,net,web3,txpool).

## Not verified
- Blockscout has not run: this workspace has no Docker. Image tags 6.8.1 and v1.35.2 are pinned; if a tag is missing, use the latest release of each.
- The /explorer page has not read a live chain from the published app yet, because no validator is reachable from the internet.

## Security
The quick explorer only sends read methods (block, transaction, receipt, balance, nonce, code) to the RPC address the user enters, with an 8 second timeout and bounded input. Do not expose Blockscout's database port; only 3000 and 4000 are published, and on a public server both go behind https (phase 9). The debug namespace is enabled on node0 only and must not be open to the internet on a validator: on a public network, run Blockscout against a separate non-validator RPC node.

## Reproduce (computer with Docker)
bash scripts/build-chain.sh
bash scripts/localnet-multi.sh init && bash scripts/localnet-multi.sh start
bash scripts/explorer.sh start, then open http://localhost:3000
bash scripts/explorer.sh status: total_blocks must grow. Send a transfer (verify-phase2.sh): it must appear with the right value in NODE.
After a new localnet init: bash scripts/explorer.sh reset, then start.
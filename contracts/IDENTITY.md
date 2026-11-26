# NODELINK on-chain identity

Deploy with Foundry from contracts/: `forge create src/NodeIdentity.sol:NodeIdentity --rpc-url "$NODELINK_RPC" --account "$FOUNDRY_ACCOUNT" --broadcast`. First confirm `cast chain-id --rpc-url "$NODELINK_RPC"` returns 778001. Publish the deployed address via a trusted channel. In Android My Identity, open On-chain Name and enter that address. Each wallet can register one permanent public display name; names can repeat, so use the wallet address to distinguish accounts. An offline messaging fingerprint is not published or changed.

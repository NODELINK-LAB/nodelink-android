# Phone wallet on nodelink-1
The Wallet tab now uses the NODELINK chain (Cosmos SDK + EVM, chain id 778001). The same recovery seed now gives an Ethereum-style secp256k1 address (0x…), the same one MetaMask and the web wallet use. The earlier ndl… address belonged to the old test ledger and never held coins.

Enter a validator's EVM RPC address (for example http://192.168.1.10:8545) and tap Save network: the app checks that the RPC really is chain 778001. Send signs an EIP-155 transfer on the phone (gas 21,000, at least the genesis minimum gas price), checks the balance covers amount plus fee, and sends it after a confirmation dialog. Refresh updates the balance and marks sent transfers Confirmed or Failed. Amounts are exact to 18 decimals.

Trust: the RPC can show a wrong balance or hide a transfer, but it cannot spend for you or change a signed transfer. Use your own validator or a trusted RPC. Plain http is allowed for local testing; use https on the internet (phase 9). Needs a network path to a validator; this is the only part of NODELINK that is not offline mesh.

## Tests
gradle :ledger-core:test covers the known secp256k1 test vector, same seed same address, recovery code restoring the same address, a signed transfer decoding to the right sender, chain 778001 and amount, exact amounts, and refused recipients. Not run yet: this workspace has no Java or Gradle.

## Acceptance
On the localnet, send 10 NODE from alloc-community to the phone's 0x address. Save network with the computer's LAN IP and EVM port, Refresh: balance 10. Send 1 NODE to another address, Refresh after about 10 seconds: Confirmed, balance about 8.99996. Point it at another chain's RPC: it must refuse. Restore the recovery code on a second phone: same address and balance. No result recorded.

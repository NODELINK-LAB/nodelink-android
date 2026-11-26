# Phase 7 report: wallets (step 1, web wallet)

## Built
- Web wallet at /wallet in the NODELINK web app. Keys stay in the user's browser wallet (MetaMask or a wallet app's browser); the page never sees them.
  - Connect wallet, show address (copy) and NODE balance from the chain (eth_getBalance), warn when the wallet is on another network.
  - Add NODELINK to the wallet (chain id 778001, NODE, 18 decimals, RPC address entered by the user) and switch to it.
  - Send NODE: address and amount checked (up to 18 decimals, exact BigInt arithmetic, not more than the balance), status shown until the block confirms it or it fails.
  - Wrap and unwrap NODE with the phase 6 WNODE contract and show the WNODE balance.

## Not verified
No public RPC exists yet (phase 9), so the page has not sent a real transaction. It can be tested now with a validator's EVM RPC on the same network.

## Android wallet moved to nodelink-1 (step 2)
- Same recovery seed and 55-character code; it now derives a secp256k1 key with a 0x address, the same address MetaMask uses.
- Talks to a validator's EVM JSON-RPC, checks the chain id is 778001, signs EIP-155 transfers on the phone (web3j crypto 4.9.8), checks amount plus fee against the balance, and tracks each sent transfer as Pending, Confirmed or Failed.
- The old Kotlin light-client ledger is no longer used by the wallet. See documentation/WalletSync.md.
- Not compiled or tested yet: this workspace has no Java or Gradle. Unit tests are in ledger-core (EvmWalletTest).

## Still to do in phase 7
- Staking from the wallet (delegate, withdraw rewards), address QR codes, transaction history (needs the phase 8 explorer).
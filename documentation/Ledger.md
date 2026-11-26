# NDL ledger and wallet, step 1
ledger-core is a plain Kotlin module shared by phones and future validator nodes. Transfers are signed with P-256 and carry the chain id and an account nonce, so they cannot be replayed or moved to another chain. A block is final only with valid votes from more than two thirds of the genesis validators (at least four, so 3 of 4); duplicate or outsider votes do not count. A block with any bad transaction changes nothing. Fees are burned. Messaging and calls never touch the ledger.

The Wallet tab has its own non-exportable Keystore key (separate from the messaging identity), shows the address and signs transfers. Signed transfers stay on the phone marked NOT confirmed: an offline signature is never a payment.

## Not built and needing decisions
No genesis, validators, coins or allocation exist. Who runs the first validators and how initial NDL is distributed must be decided by you before a testnet. Still to build: validator node, block proposal and voting rounds, transaction and block sync to phones, wallet backup and recovery. Issuing a coin may be regulated where you operate; get legal advice before any real value.

## Tests
gradle :ledger-core:test covers quorum, duplicate and outsider votes, replay, overspend, tampering, wrong parent and atomic failure. No result recorded.

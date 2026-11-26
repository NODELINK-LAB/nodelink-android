# NDL validator node (testnet)
A plain Kotlin program for a computer or server (JDK 17), not the phone. Build: gradle :validator-node:installDist, then use validator-node/build/install/validator-node/bin/validator-node.

## Four-validator test on one computer
1. Run keygen v1 … keygen v4. Each prints a validator line and its address. Keep key.private secret; it is a plain file on disk.
2. Write one genesis.txt and copy it into all four folders:
   chain ndl-testnet-0
   validator <hex from v1> … (one line per validator)
   alloc <address from v1> 1000
3. In each folder write peers.txt with the other three, e.g. 127.0.0.1:39502.
4. Start: run v1 39501, run v2 39502, run v3 39503, run v4 39504 (four terminals).
5. Send: transfer v1 127.0.0.1:39501 ndl-testnet-0 <another address> 10 0. Within about 15 seconds all four must print Committed height 1.
6. Stop one validator and send nonce 1: the other three must still commit. Stop two: nothing may commit. Restart them: they must catch up to the same height.
7. Resend nonce 0: it must be rejected (replay).
No result recorded.

## How it works and limits
Proposer rotates by height and round; a round lasts 15 seconds. Each validator signs at most one block per height and records it before voting, so conflicting commits need more than a third of validators to be dishonest. Blocks are final with more than 2/3 votes; lagging validators fetch committed blocks and verify the votes. Fees are burned. Limits: if votes split at one height the chain can stall until a fix (no full Tendermint locking yet); messages travel over plain TCP, signed but not encrypted, so run it on a private network; no validator set changes, no slashing, no rate limiting beyond fixed sizes, no fsync guarantee, no independent audit. Phones cannot submit or read the chain yet.

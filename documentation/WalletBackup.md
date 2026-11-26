# Wallet backup and recovery
The wallet key is now derived from a random 32-byte seed, stored on the phone encrypted with the Keystore AES key. Show recovery code displays it as 55 characters in groups of five (letters and digits, no I L O U), with a checksum that catches typos; screenshots are blocked while it is shown. Restore from recovery code on any phone gives the same address and balance after a sync. Restoring replaces the wallet on that phone and clears its unsent transfers.

The earlier Keystore wallet key could not be exported, so it was replaced; its address had no coins because no network existed. The messaging identity is separate and unchanged. Anyone who sees the code can spend the wallet; it is never sent anywhere. The seed is protected by the phone's Keystore but is no longer hardware-only. No PIN or biometric lock on the wallet yet.

## Tests
gradle :ledger-core:test also covers derived keys signing and verifying, same seed same address, code round trip, typo and length rejection. No result recorded.

## Acceptance
Show the code, write it down, sync and note the balance. Uninstall, reinstall, Restore with the code, sync: same address and balance. Change one character: restore must be refused.

# NODELINK Foundation field kit
Status: source candidate. Not compiled or tested on Android in the browser workspace. No measurements yet.

## Build
Install JDK 17, Gradle 8.9, Android SDK 35, platform-tools and Android Studio Ladybug or compatible. Set ANDROID_HOME and JAVA_HOME. From the extracted root: gradle :android-app:assembleDebug. Install with adb -s SERIAL install -r android-app/build/outputs/apk/debug/android-app-debug.apk on each phone. Dependency download needs internet initially; running the probe does not.

## Physical acceptance test
Use two compatible Android 10+ devices. Disable cellular data and disconnect from infrastructure Wi-Fi, leaving the Wi-Fi radios on. Enable Location services if discovery requires it. Open both apps; grant Nearby devices (or Location on Android 10–12), then tap Discover again. Cross-check the complete 64-character peer fingerprints directly on the two physical screens and enter each on the other phone before testing. A device name and short Network ID are NOT authentication. Select a peer on one device and accept the group dialog. Start the probe on the group owner, then on the client within 60 seconds. Both must report a verified probe. Socket permission INTERNET authorizes local sockets, not an internet dependency.

## Negative tests and measurements
Change one hex digit on either phone: handshake must fail, no successful probe. Remove the peer during authentication: timeout or failure, never success. Restart and compare public fingerprints. Uninstall/reinstall creates a new identity. Disconnect, discover again, repeat ten runs, record device models, OS versions, discovery-to-list and connect-to-ack timings and failures. No result is recorded automatically here.

## Threat model
Wi-Fi Direct device names and addresses are untrusted discovery hints. TLS 1.3 uses mutual certificates and out-of-band full SHA-256 public-key pins. Session keys and TLS sequencing protect payloads against passive reading, modification and replay; a fresh random challenge binds the reply to the probe. Certificate keys use P-256 in Android Keystore; hardware backing depends on device. Local radio observers still see metadata. Physical fingerprint exchange is essential. A compromised endpoint can read its own traffic. Pin changes require manual re-verification; no global revocation, identity recovery, or secure export yet. Foreground, one group, one bounded probe only. Timeouts and fixed frame sizes bound individual sessions but cannot prevent radio jamming or repeated denial of service. No independent audit.

## Architecture and next gates
android-app: lifecycle, permissions, radio discovery and group negotiation. identity-core: non-exportable signing keys and pinned trust. network-core: ephemeral direct authenticated challenge/ack. No central service. Split out BLE discovery and radio adapters when a second transport is tested. Next: durable encrypted messages, acknowledgments and expiry; AudioRecord/Opus/AudioTrack direct calls; then three-device forwarding using bounded hops, authenticated neighbors, duplicate suppression, end-to-end payload security and bandwidth limits. Measure jitter and packet loss before promising multi-hop voice.

## Relay and ledger feasibility
Candidate bench relay: Raspberry Pi 4 with two independently supported Wi-Fi adapters, regulated 5 V supply, matched certified antennas and enclosure. Validate independent channels, current draw, link budget and regional EIRP restrictions first; this is not validated hardware. Require authenticated signed firmware and rollback protection. ESP32 forwarding capacity for voice is unmeasured.
For a later independent NDL testnet evaluate Cosmos SDK with CometBFT, four independent validators, deterministic genesis and an account ledger with sequence numbers. Never treat offline signatures as confirmations; a partition lacking quorum must stop finalization. No genesis, assets, wallet, token allocation, rewards or transactions exist in this kit. Do not pay self-reported forwarding claims. Voice and messaging never touch the ledger.

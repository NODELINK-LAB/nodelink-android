# Auto relay (foreground)
With Auto relay on, the app scans every 45 seconds, connects to one available phone it has not tried in 10 minutes, runs a mesh sync and leaves the group. Relay sessions accept any NODELINK identity (no fingerprint pin) because relayed content is end-to-end encrypted and signed; a stranger can only pass envelopes on, not read or forge them. Direct chat, calls and probes still require the pinned fingerprint.

Limits: works only while the app is open on screen; both phones need Auto relay on. Android may show a Wi-Fi Direct invitation the other person must accept. A non-NODELINK device is tried once, fails, and is skipped for 10 minutes. Turn it off before direct chat or calls, because it leaves the group after each sync.

## Acceptance
Three phones with Auto relay on, A and C out of range of each other, B between. Send A to C via mesh. Without tapping sync, C must receive it and A must later show Delivered via mesh. Record times. No physical results recorded.

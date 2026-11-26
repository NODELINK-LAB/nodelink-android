# Calls and messages through a phone without NODELINK
Any phone can carry NODELINK traffic without the app if its owner turns on the phone's own Wi-Fi hotspot: the hotspot forwards packets between the phones joined to it. It needs no SIM, no mobile data and no internet (on most Android phones; some phones or carriers refuse a hotspot without SIM, and iPhone Personal Hotspot needs a cellular plan). Any Wi-Fi router without internet works the same way. One of the two NODELINK phones may also be the hotspot itself.

A and C join the same hotspot, select each other's contact and tap Link over Wi-Fi / hotspot (or Call). Each phone broadcasts its fingerprint on UDP 39431 every 2 s; the higher fingerprint listens on TCP 39432 and the lower connects. Then the usual pinned mutual TLS 1.3 link runs: ringing, voice, call quality and automatic messages, status "Linked over Wi-Fi/hotspot".

Security: the hotspot phone and anyone on that Wi-Fi see only encrypted traffic, sizes and timing, plus each NODELINK phone's fingerprint in the beacon (who is present). A fake beacon can only make a connection fail. Blocked identities are refused as everywhere.

Limits: range is one hotspot (typically 30 to 100 m indoors, more outdoors); some hotspots isolate their clients, then nothing connects; a phone without NODELINK cannot be made to forward anything without its owner turning on the hotspot, and nobody's phone may be used without consent. Mesh auto relay still uses Wi-Fi Direct. No physical results recorded.

## Acceptance
Phone B (no NODELINK, SIM removed) turns on its hotspot. A and C join it, select each other, tap Link over Wi-Fi / hotspot: both show Linked over Wi-Fi/hotspot. Message A to C arrives without tapping. Call A to C: rings, both hear. Turn off B's hotspot: Link lost within 10 s. Record which phone models allow a hotspot without SIM and whether clients can reach each other.

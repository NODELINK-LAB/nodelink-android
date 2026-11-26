# QR fingerprint exchange
Each phone shows its full fingerprint as a QR code (nodelink:<64 hex>). Scan peer QR fills the direct-peer field; Scan recipient QR fills the mesh recipient. Scanning is offline and bundled in the app (ZXing); camera permission is asked on first scan. Scanning in person is the verification step: a QR shown on someone else's screen proves nothing about who they are. Opening the scanner ends any running session, so scan before connecting.

## Acceptance
Scan each other's codes: fields must match the displayed fingerprints. Scan an ordinary QR: it must be rejected. Deny camera permission: no crash.

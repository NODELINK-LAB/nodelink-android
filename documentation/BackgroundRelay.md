# Background relay
The Auto relay switch now also starts a foreground service with a permanent "NODELINK relay on" notification. It keeps scanning every 45 seconds and syncing mesh envelopes while the app is closed or the screen is off, and restarts if Android stops it (START_STICKY). The setting is remembered. While the app is on screen the service stays idle and the app runs the same loop itself, so the two never use the radio at once; opening the app ends any background sync in progress, and unacknowledged envelopes stay queued.

Costs and limits: a partial wake lock keeps the CPU awake, so expect noticeable battery drain. Some manufacturers (Xiaomi, Oppo, Vivo, Samsung battery savers) still kill background services; exempt NODELINK from battery optimisation. Android may show a Wi-Fi Direct invitation that someone must accept on screen. It does not start after a reboot until the app is opened once. Android 10 to 12 use a location-type service because Wi-Fi Direct needs location permission there; Play Store review will require a declaration for it.

## Acceptance
Three phones, Auto relay on, all apps closed and screens off, A and C out of range, B between. Queue A to C before closing. Within minutes C must show the message when opened and A must later show Delivered via mesh. Record battery drain per hour and any kills per phone model. No physical results recorded.

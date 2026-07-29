# Known device behaviors

## SOV41 / Android 11 / build 55.2.C.3.21

- The device exposes Qualcomm `pedometer` step-counter sensors in wake-up and
  non-wake-up variants.
- Removing the StepArena card from Recents left the foreground service,
  process, notification, and sensor listener running in the observed test.
  This is SOV41 evidence, not a universal Android guarantee.
- `am kill <package>` did not terminate the active foreground-service process.
  `am crash <package>` did terminate it without force-stop semantics; Android
  recreated the START_STICKY service after about one second.
- Android Settings force-stop recorded ApplicationExitInfo reason
  `USER_REQUESTED` with a description beginning `stop
  com.lazyapps.steparena`, removed the notification, and did not auto-restart.
- Android 11 has no notification runtime permission. `areNotificationsEnabled`
  is the relevant notification diagnostic.
- Short automated observation cannot establish screen-off walking or battery
  optimization behavior. Those cases require timed physical walking.
- A Debug-only screen-off simulation on this device kept one Service and one
  PID/session while a synthetic delta was persisted and reflected in the
  notification. This validates the lifecycle/data path only, not real sensor
  delivery while the screen is off.
- Activity-absent synthetic input was processed once and persisted by the
  foreground Service. This does not establish Activity-absent physical walking.
- Battery-optimization enabled/exempt waiting can establish only Service,
  notification, PID, session, and Heartbeat survival. Without walking it cannot
  support a claim that optimization exemption is unnecessary.

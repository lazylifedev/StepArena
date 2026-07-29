# Tracking requirements

- Tracking starts only after explicit user action, activity-recognition
  permission, and step-counter availability checks.
- A health foreground service owns the sensor listener. Duplicate start
  commands must not register duplicate listeners.
- `START_STICKY` may recreate a normally terminated service when
  `trackingRequested=true`; it cannot override Android force-stop.
- Every service process instance has a new session ID. Explicit stop clears the
  session and baseline.
- Notification updates are throttled to 10 steps, 15 seconds, or a forced
  heartbeat update. The channel is LOW, silent, and non-vibrating.
- A notification stop PendingIntent carries its session ID. An action from an
  older session is ignored.
- Heartbeat is independent of sensor events and updates every five minutes.
- Debug diagnostics show permissions, battery optimization, sensor support,
  session, baseline, last value, today steps, heartbeat, notification time, and
  the previous exit summary.
- Fake Sensor controls exist only in the Debug UI. Enabling them unregisters
  the real sensor first.
- Unverified physical-device scenarios remain pending and are never promoted
  to a pass based only on Fake Sensor or automated tests.

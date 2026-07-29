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
# Phase 3 detector integration

The foreground service registers Step Counter and, when available, Step Detector once.
Stopping the service unregisters both. Debug fake-counter mode unregisters real sensors,
so synthetic and physical inputs cannot be mixed.
# Phase 4 monitoring

Heartbeat の既定間隔は 5 分、警告は 12 分、重度判定は 30 分とする。
stale 時は同じ gap の初回だけ通知し、明示停止中は通知しない。
バックグラウンドから Service、権限画面、バッテリー設定画面を無条件に開始しない。

# Phase 6 permission UX

ACTIVITY_RECOGNITIONは計測開始操作の直前、POST_NOTIFICATIONSは計測通知が必要な直前に
用途を説明して要求する。拒否後にループせず、記録・設定・診断を利用可能にする。
バッテリー設定はtrackingRequested、複数回stale、最適化対象、説明未確認、非明示停止を満たす場合だけ案内する。

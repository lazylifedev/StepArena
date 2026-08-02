# Phase 7.4.15C audit

## Tracking service recovery

- `StepArenaApplication.onCreate` schedules workers and game maintenance, but does not reconcile the tracking service.
- Both release and debug `MainActivity` only observe `TrackingStateRepository`; neither activity lifecycle called a service-start helper after onboarding.
- `HomeViewModel.startTracking` starts the foreground service only for an explicit user action. A process killed during APK replacement can therefore leave persisted `serviceRunning`, sensor-registration flags, `TRACKING`, and an old heartbeat without recreating the service.
- Persisted `serviceRunning`, `stepCounterRegistered`, `stepDetectorRegistered`, `trackingStatus`, and `lastHeartbeatAt` are last-reported diagnostics. They are not current liveness proof. `trackingRequested` remains the durable user intent.
- Home mapped `trackingRequested=true` directly to `SessionState.TRACKING` and mapped `TRACKING` to an active status without checking heartbeat freshness. This explains the healthy UI while no OS service existed.
- `StepTrackingService.restoreAndRegister` already restores `lastSensorValue` through `TrackingStateRepository` and `StepCounter`; repeated Android start requests are serialized by the service instance and `setupStarted`. It is therefore the safe idempotent recovery boundary. A process-local registry is diagnostic only.
- No `BOOT_COMPLETED` or `MY_PACKAGE_REPLACED` receiver existed. The recovery receiver now attempts an idempotent start for both events; if Android rejects the background start, foreground activity reconciliation remains the guaranteed retry path.

## Game cold-start writes

- `StepArenaApplication.onCreate` calls `gameRepository.runMaintenance()` and also collects the current day to call `ensureMatch`. A receiver-created process runs the same application initialization.
- `ensureMatch` updated an active daily match unconditionally with `updatedAtEpochMillis=now`, even when its competitive fields were unchanged.
- `rebuildLeague` unconditionally upserted the weekly league with both timestamps set to `now`, then deleted and recreated every participant with `updatedAtEpochMillis=now`.
- These paths account for the observed changes to `daily_matches.updatedAtEpochMillis`, `weekly_league_participants.updatedAtEpochMillis`, and both weekly league timestamps.
- The repair receiver itself was not required for the timestamp writes; it merely cold-started the application process.

## Remediation boundary

- Foreground reconciliation sends an idempotent start request when onboarding is complete, activity recognition is granted, and durable user intent is true. Persisted liveness flags never suppress it.
- UI health requires a fresh heartbeat in addition to user intent and a valid tracking status.
- Game writes compare semantic content, preserve `createdAtEpochMillis`, and update `updatedAtEpochMillis` only when content changes.
- The completed 204-step repair broadcast bridge is removed. The separate timestamp repair remains debug-only, manifest-bound, fixed-column, transactional, and is not executed in Phase 7.4.15C.

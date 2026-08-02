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
- The completed 204-step repair broadcast bridge is removed. The unexecuted Phase 7.4.15D timestamp repair bridge is also removed; no production-data repair receiver remains in Production Debug, Production Release, or QA.

## Preserved timestamp audit history

- At 2026-08-02 17:24:19 JST, the old repair receiver cold-started the application process and the unconditional game initialization writes changed four timestamp columns. The receiver did not execute a timestamp repair.
- The saved PRE/POST comparison records `daily_matches.updatedAtEpochMillis` as `1785654238578` to `1785659058540` for the active match (2 rows before and after).
- It records `weekly_league_participants.updatedAtEpochMillis` as `1785654238657` to `1785659058736` for all 10 participants (10 rows before and after).
- It records both `weekly_leagues.createdAtEpochMillis` and `weekly_leagues.updatedAtEpochMillis` as `1785654238657` to `1785659058736` for the single league row (1 row before and after).
- Saved validation after subsequent normal Activity launches equals the POST snapshot for every affected row. Content values, primary keys, row counts, match outcomes, ratings, points, and step counts did not change.
- The cause was the now-corrected unconditional match/league upsert and participant delete/recreate behavior. Game initialization now preserves `createdAtEpochMillis`, does not update `updatedAtEpochMillis` when semantic content is unchanged, and does not recreate unchanged participants.
- The historical timestamps will not be corrected. The device's current values remain authoritative so that a later legitimate game update is never rolled back.
- Evidence: `backups/SOV41_20260802_181450_QV7209CF25_PHASE_7_4_15B1_FINAL/timestamp-diff-summary.txt` and its saved PRE/POST/after-launch database comparison.

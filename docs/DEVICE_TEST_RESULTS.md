# Phase 2.1 device test results

## Test environment

- Date: 2026-07-29 (Asia/Tokyo)
- Device: Sony SOV41 (`QV7209CF25`)
- Android: 11 / API 30 / build `55.2.C.3.21`
- App: `1.0.0` (`versionCode=1`), Debug
- Battery: 100%, not charging (`status=5`)
- Battery optimization: optimized
- Activity recognition: initially denied, then granted
- Notifications: enabled (Android 11 has no notification runtime permission)
- Step counter: supported (`pedometer`, type 19, wake-up and non-wake-up)

Device serial is recorded only in this local test document and is not written
to the in-app diagnostic log.

## Results

| Scenario | Procedure and evidence | Expected | Actual | Result |
|---|---|---|---|---|
| Clean install | `pm clear`, cold launch | Page 1 of 7 | Page 1 shown in Japanese | Pass |
| Onboarding order/back | Advanced to page 2, tapped Back | Return to page 1 | Returned to page 1 | Pass |
| Permission denial/retry | Denied activity recognition on page 3 | No crash; remain able to retry | Stayed on page 3; second request succeeded | Pass |
| Onboarding process interruption | Killed background process on page 4 and relaunched | Resume page 4 | Resumed page 4 | Pass |
| Completion | Completed page 7 and relaunched | Home, no onboarding replay | Home shown | Pass |
| Baseline | Started real service | Initial cumulative value not added | baseline=0, today=0 | Pass for observed baseline; physical delta pending |
| Fake sensor | 0→100→100→20→20000 and invalid floats | add once, ignore duplicate/invalid, rebaseline decrease | steps 0→100→100→100→20080; invalid unchanged | Pass |
| Explicit stop | App stop confirmation | service/notification removed; retain confirmed steps | both removed; 20080 retained | Pass |
| Restart | Start after explicit stop | new session and new baseline | session `3894…`→`8bea…`; baseline/last unset before event | Pass |
| Process recreation | `am crash` while FGS active (not force-stop) | START_STICKY recreation | PID 14122→16480, restartCount=1, notification restored | Pass |
| Process restoration data | Reopen diagnostics | new session; no rollback/double add | session `56fd…`; baseline=0; steps remained 20080 | Pass |
| ApplicationExitInfo | Inspect debug diagnostics after recreation | one processed reason with required fields | CRASH, description, importance, timestamp, process, pss, rss shown | Pass |
| Recents swipe | Manually swiped centered StepArena card upward | Activity removed; service behavior recorded | Activity removed; PID/service/notification remained | Pass |
| Settings force-stop | Android App info → Force stop → OK | no automatic recovery | PID/service/notification absent after 5 seconds | Pass |
| Launch after force-stop | Launch app manually | stopped state; no hidden auto recovery | `計測開始前`, start action shown, steps retained | Pass after fix |
| Notification channel | `dumpsys notification` | LOW/no sound/no vibration | foreground notification flags present; channel configured LOW/no sound/no vibration | Pass |
| Notification throttle | Fake sensor and unit policy | 10 steps or 15 seconds/force | policy tests pass; device log did not update per step | Pass |
| Screen off walking | Requires moving device for 5–10 minutes | count continues | No physical operator available | Not run |
| Activity closed walking | Requires moving device for 5+ minutes | count continues | Service persistence confirmed; walking delta not measured | Partial |
| Real walking 20–100 steps | Requires physically walking SOV41 | only post-baseline delta counted | No physical operator available | Not run |
| Stop/walk/restart/walk | Requires approximately 100/50/50 real steps | stopped interval excluded | State transitions verified with Fake Sensor; real walking not run | Partial |
| Battery optimization A/B | Requires two 10+ minute walking/wait conditions | record both conditions | Optimized condition identified; physical A/B not run | Not run |
| Date change on device | Avoid affecting other apps | unit/debug first | Unit coverage only; device date unchanged | Partial |

Phase 2.1 is **not complete** until the rows marked Not run/Partial receive
physical walking evidence. They must not be inferred from Fake Sensor,
instrumentation, service state, or notification state.

## Defects found and retest

1. Debug build showed fixed demo data rather than the real tracking service.
   It now uses the real UI/service and exposes bounded debug diagnostics/Fake
   Sensor controls. Retest: real foreground service and sensor registration
   observed.
2. App crashed when Android attempted to instantiate `HomeViewModel`. Added the
   standard `Application` constructor. Retest: clean cold launch passed.
3. User-facing strings were corrupted. Replaced affected resources and
   onboarding/diagnostic copy. Retest screenshots/UI hierarchy are readable.
4. Permission denial advanced onboarding and prevented a retry. It now remains
   on page 3 until granted. Retest passed.
5. Explicit restart retained old baseline/session. Stop/start now clears the
   baseline and creates a new session. Retest passed.
6. Date rollover discarded the first delta. The previous sensor value is now
   retained across the day boundary; unit retest passed.
7. Force-stop left persisted state appearing active. Startup now reconciles
   Android 11 `USER_REQUESTED` entries whose description starts with the
   package-specific `stop` form. Retest showed stopped state.
8. Package replacement also produces `USER_REQUESTED` with a description
   beginning `stop <package>`, so it was incorrectly reconciled as a Settings
   force-stop. A following Debug foreground-service injection then timed out
   before promotion and crashed with `RemoteServiceException`. The classifier
   now excludes `installPackageLI`, the Debug path promotes synchronously, and
   unit coverage was added. The optimized-wait trial was restarted after the
   fix; the pre-fix trial is a failed test and is not counted as endurance
   evidence.
9. Debug Fake mode was not restored after process recreation, which would have
   switched a synthetic lifecycle test back to the real listener. Debug mode
   selection is now persisted separately, START_STICKY restores it without
   registering the real listener, and same-value/+25 retest passed after PID
   27470 -> 27513.

## Diagnostic artifacts

Local XML/PNG evidence is under `screenshots/phase21_*`. Diagnostic logs retain
at most 200 tab-encoded entries in the Debug build. They exclude account data,
paths, and device serials.

## Phase 2.1 non-walking continuation

Date: 2026-07-29 (Asia/Tokyo). Device: SOV41, Android 11. These results use
Debug-only synthetic cumulative sensor values. They do not prove physical
step-counter behavior.

### Pass

- Clean data reset, onboarding, permission recovery, foreground-service
  creation, notification channel configuration, task removal, `am crash`
  recreation, Settings force-stop behavior, and ApplicationExitInfo capture
  were executed on SOV41.
- The notification channel is LOW with no sound and no vibration.
- Release merged manifest and APK scans contain no Debug fake receiver, fake
  action, Debug evaluation sheet, or Debug-only label.

### Non-walking simulation pass

| Scenario | Start | Input/end | Evidence | Result |
|---|---:|---:|---|---|
| Clean Fake baseline | 0 | raw 10000 | baseline 10000, today 0, one service | Pass |
| Increment | 0 | raw 10050 | delta 50, today 50, notification 50 | Pass |
| Screen-off path | 50 | raw 10080 | delta 30 once, today/notification 80, PID/session retained | Pass |
| Activity-absent path | 80 | raw 10100 | delta 20 once, today/notification 100, one service | Pass |
| Duplicate/invalid/reset/large delta | synthetic sequence | same, lower, NaN, infinities, large increase | unit and device diagnostic evidence; no negative/double addition | Pass |
| Notification throttling | 1 and 10 step policies | time/force cases | 10-step/15-second/forced policy tests | Pass |
| Date/zone boundary | previous sensor retained | first new-period value | first difference retained exactly once | Pass |
| Optimized idle wait | PID 25715, session `d377…`, Service/notification active | 10m10s screen locked | same PID/session, one Service, Heartbeats at 5m and 10m, notification refreshed | Pass for idle survival only |
| Optimization-exempt idle wait | same PID/session and Service | 10m10s screen locked | same PID 25715/session, one Service, Heartbeats/notification continued | Pass for idle survival only |
| Explicit stop/restart | today 100, raw 10100 | stopped raw 10150; restart baseline 10150; raw 10200 | stopped input ignored, new session `4f6f…`, delta 50 once, today 150 | Pass |
| Stale stop action | current session `4f6f…` | old synthetic session | `stale_stop_ignored`, Service remained one | Pass |
| `am crash` Fake restoration | PID 27470, today 150, raw 10200 | PID 27513; same 10200 then 10225 | START_STICKY, new session `3eeb…`, Fake restored without real registration, delta 25 once, today 175 | Pass |
| Repository instrumentation | persisted today 50/raw 10050 | reload then raw 10075 | today 75, no duplicate | Pass (1/1) |
| Compose UI initial run | locked SOV41 | 6 tests | Activity blocked by pattern lock; `No compose hierarchies found` | Environment failure |
| Compose/Instrumentation retest | unlocked SOV41 | 6 Compose + 1 repository test | 7/7 passed in 21.7s | Pass |

The screen-off and Activity-absent rows validate Service, DataStore,
notification, and UI data paths only. They are not real-sensor screen-off or
walking passes.

### Not verified - physical walking required

- Real walking 20-100 steps.
- Screen-on real-walking increment.
- Screen-off real walking.
- Real walking after Activity removal.
- Exclusion of actual steps taken while explicitly stopped.
- Real walking after restart.
- Real-walking comparison with battery optimization enabled vs exempted.
- Low-memory or ordinary OS process termination; `am crash` is not equivalent.

These remain **not run / pending physical walking**. No Fake Sensor result may
be used to change their status.

### Debug injection protocol

The Debug manifest alone exports
`com.lazyapps.steparena.debug.FAKE_SENSOR`. Example:

```text
adb shell am broadcast -a com.lazyapps.steparena.debug.FAKE_SENSOR \
  -n com.lazyapps.steparena/.debug.DebugFakeSensorReceiver \
  --es command start --ef value 10000
```

Commands cover baseline/start, exact value, increment, timed sequence, reset,
date-change equivalent, timezone-change equivalent, stop, stale stop, and
onboarding preparation. Exact values also cover duplicate, lower, large,
NaN, positive infinity, and negative infinity. Every command emits a
`DEBUG ONLY` warning; selecting Fake mode unregisters the real listener.

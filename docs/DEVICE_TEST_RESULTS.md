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

物理歩行試験は公開前必須ゲートとして未完了。
Phase 3以降の開発は継続可能だが、Google Play公開判定には使用しない。

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
# Phase 3 non-walking validation (2026-07-29)

Device: SOV41 (`QV7209CF25`), Android 11.

- Connected instrumentation and Compose tests: 8/8 passed.
- Fake Step Counter baseline 10,000 followed by +120 steps created
  `step_arena.db`, updated the home to 120 steps, 0.08 km and 3 kcal, retained
  unknown walking time/speed as `―`, and displayed the estimated-data notice.
- The records destination displayed the 24-hour graph, five metric selectors,
  and the persisted activity after navigating away from Home.
- Evidence: `screenshots/phase3_home.xml` and
  `screenshots/phase3_records.xml`.

This is non-walking simulation evidence only. Physical-walking acceptance remains
open for hourly placement, duration, distance/calorie/speed plausibility, automatic
session boundaries, screen-off operation, Activity termination, stop/restart, and
battery-optimization comparison.
# Phase 3.1

SOV41 (`QV7209CF25`, Android 11) ran the existing DAO, instrumentation, and
Compose suite: 8/8 passed. This was a non-walking validation. Real detector event
timing, walking duration and boundaries, screen-off behavior, metric accuracy,
and battery-optimization comparison remain physical-walk gates.
## Phase 3.2 pending physical gates

Automated fake-sensor results are not physical-walking evidence. SOV41 physical
walking remains required before release for real manual start/end, screen-off,
Activity removal, process recreation, paused detection, five-minute automatic end,
distance, calories, speed, and battery-optimization comparison.

On 2026-07-29, SOV41 (`QV7209CF25`, Android 11) passed the dedicated Room 1 to 2
migration instrumentation test (1/1) and the complete debug instrumentation suite
(13/13), including DAO and Compose UI coverage. These results validate synthetic and
database behavior only and do not close the physical gates above.
# Phase 4

2026-07-29 に SOV41 `QV7209CF25` / Android 11 (API 30) で 16 件の instrumentation を実行し成功した。
端末の package 一覧に Health Connect Provider はなく、実 SDK availability 呼出がクラッシュしないこと、
Fake source の 100 歩補完と二重実行防止、Migration 2→3、DAO、Compose UI を確認した。

実 Provider がないため、権限拒否・許可、実 StepsRecord 読取、実 DataOrigin は公開前ゲートとして残る。
Provider がない場合も通常の Step Counter 計測を継続する。
物理歩行試験も引き続き未完了である。
# Phase 5

SOV41 Fake Counterおよび物理歩行の検証結果は、実行日時・APK・commitとともに追記する。物理歩行試験は公開前ゲートとして未完了のまま扱う。

## 2026-07-29 SOV41

- Device: SOV41 / Android 11 / `QV7209CF25`
- `connectedDebugAndroidTest`: 19/19 成功
- Migration 3→4、既存DB維持、DAO、既存Home/Compose UI、WorkManager関連テストを含む
- Debug APKのインストールとMainActivity起動を確認
- Fake Counterによる5,000歩、勝敗3種、昇格、週間順位、シーズン、実績、補完率、UNKNOWN、再起動、日付変更の一連の手動シナリオは未実施
- 物理歩行試験は公開前ゲートとして未実施
# Phase 5.1

SOV41（Android 11）でDebugシナリオA〜E、DAO/Migration/Instrumentation/Compose UIを
実施して結果を追記する。Debugシナリオ合格を物理歩行試験合格とは扱わない。
物理歩行、画面OFF、プロセス復元、距離・カロリー・速度、バッテリー最適化は未検証。

## 2026-07-29 SOV41

- 端末: SOV41 / Android 11 / `QV7209CF25`
- connected Debug Android Test: 22/22成功
- Migration 4→5、既存DAO/Instrumentation、Debug Compose UIを含む
- Debugメニューを音量下長押しで開き、「開発用ゲームシナリオ」、警告文、歩数操作を実画面で確認
- Counter +5,000を確認ダイアログ経由で2回実行し、対戦画面で10,000歩を確認
- force-stop後の再起動でも対戦有効10,000歩・総歩数10,000歩の維持を確認
- `am crash` 後に再起動し、MainActivityの再生成と前景復帰を確認
- 手動シナリオA〜Eの全操作証跡は未完了。自動試験成功のみを手動合格とは扱わない

## 2026-07-29 Phase 5.1 再試験

- Play Protectの未回答ダイアログを「送信しない」で解除し、Debug APKとandroidTest APKの直接インストールに成功
- `connectedDebugAndroidTest`: 再実行25/25成功（初回の一過性Composeホスト未生成は単独1/1成功）
- Debug初期化のMain-thread Roomクラッシュ、時間別歩数未反映、モード切替graph残留、Silver境界Debug値を修正
- シナリオA: 5,000歩、時間別5,000、WIN、rating 1,025、二重確定防止、再起動／force-stop維持
- シナリオB: RECOVERED 1,000→有効800・制限200
- シナリオC: Silver昇格1,600、Bronze降格1,599
- シナリオD: 日・週・月境界と次期間生成を確認
- シナリオE: 通知候補3件、同日再処理後もdeduplication key 3/3
- 通常DBはrating 1,000、勝数0、歩数0、DailyMatch 1、通知0、実績0のまま不変
- 物理歩行、画面OFF、距離・カロリー・速度、バッテリー最適化は未検証

## Phase 6待ちゲート（2026-07-29）

SOV41でのオンボーディング、SAF ZIP、ゲーム初期化、全削除、TalkBack、200%文字、
force-stop、`am crash`再試験はPhase 6実装後に実施する。未実施項目を合格扱いしない。
20～100歩／約1,000歩の物理歩行とHealth Connect実Providerは引き続き本番公開前ゲート。

### Phase 6実行結果

- SOV41 / Android 11へDebug APKをinstallし、アプリデータ削除後のcold startを確認。
- Landscape（rotation 3）でオンボーディング1/5、見出し、本文、「次へ」が表示され、操作領域は126dp相当。
- `connectedDebugAndroidTest`は25件中18件前後まで進行後、Composeテストホスト消失
  (`No compose hierarchies found`) が試行ごとに別Homeテストへ移動して再現。全件合格扱いにしない。
- 既存の停止警告テストは画面外要素へscrollしてから検証するよう到達性を修正。
- TalkBack、200%文字、データエクスポート、ゲーム初期化、全削除、force-stop、`am crash`の
  Phase 6手動シナリオは未完了。

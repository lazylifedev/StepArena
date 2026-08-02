# Data reliability

`TYPE_STEP_COUNTER` is boot-cumulative. StepArena never treats its first value
as today's count. The first accepted event establishes a baseline; later
non-negative differences are added once.

- Duplicate values add zero.
- NaN, infinities, and negative values are ignored.
- Sensor decreases or boot-session changes establish a new baseline without
  removing confirmed steps.
- Explicit stop clears baseline/last value so stopped-time movement is not
  imported on restart.
- Process recreation retains last value and confirmed steps so the next sensor
  event contributes only one difference.
- A local date/zone boundary finalizes the old day, resets today's total, and
  preserves the previous sensor value so the first new-day difference is not
  lost.
- Phase 2.1 does not backfill stopped intervals from Health Connect.

An unusually large difference is retained but marked for review. Debug logs
are capped at 200 entries. Release builds do not append detailed diagnostic
logs.

Debug builds provide an explicitly labelled synthetic cumulative-sensor bridge
for lifecycle testing. Entering this mode unregisters the real sensor listener;
real and Fake Sensor inputs are never intentionally active together. The bridge
is for Service/DataStore/notification/UI path validation and is not physical
walking evidence.

The Debug bridge supports exact, incremented, repeated, decreased, non-finite,
reset, date-boundary, and timezone-boundary inputs. Explicit stop clears the
baseline and last value. Restart establishes a new baseline, so a stopped-time
cumulative increase is excluded and only post-restart differences are added.

Release builds neither log detailed diagnostic entries nor declare the Debug
receiver/action in their merged manifest.
# Phase 3 metric quality

Activity metrics retain `MEASURED`, `ESTIMATED`, `RECOVERED`, `MIXED`, or `UNKNOWN`
quality. Unknown values are not converted to zero. Step Counter remains authoritative;
Step Detector only improves timing, walking-duration, and session-boundary estimates.
# Phase 3.1 quality retention

Recovered unclassified steps retain `RECOVERED` quality across daily rebuilds.
Daily quality merges hourly quality with unclassified quality. Home reliability
is derived from the stored quality instead of treating every record as estimated.
## Manual walk invariants

Daily steps equal hourly steps plus unclassified steps. A manual session is a
non-additive view of the same accepted counter deltas. Processing-state idempotency
prevents a counter value from being accepted twice, and session routing selects
exactly one of automatic or manual. Completed sessions reject later deltas.
`activeDurationSeconds + pausedDurationSeconds <= elapsedDurationSeconds` is enforced
when updating and finalizing sessions. Missing detector evidence lowers duration and
speed quality rather than inventing active time.
# Phase 4: 欠測補完

自前 Step Counter を正本とし、Health Connect は明示された gap だけを補完する。

## 歩幅の自動推定

歩幅の自動推定は `DefaultStepLengthEstimator` と同じ `身長(cm) × 0.415` を使用する。
画面では小数第1位までを目安として表示し、距離計算では丸める前のメートル値を使用する。
自動推定を無効にした後の新規記録には、利用者が保存した手動歩幅を使用する。
外部レコードは origin と ID/fingerprint を保持し、同一区間・同一レコードの二重加算を防ぐ。
品質は直接利用可能なら `RECOVERED`、配分を含む場合は `MIXED`/`ESTIMATED`、
判断不能なら `UNKNOWN` とする。明示停止区間は既定で補完しない。
# 対戦利用

画面上の実歩数と対戦有効歩数を分離する。補完・推定・UNKNOWN・異常値は`COMPETITIVE_STEP_POLICY.md`に従い制限または除外し、元の活動記録は保持する。

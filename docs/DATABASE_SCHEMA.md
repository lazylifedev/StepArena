# Database schema

StepArena uses Room database `step_arena.db`. The initial schema version is **1** and its
exported JSON is tracked under `app/schemas/`.

Version 1 contains `hourly_activity_records`, `daily_activity_records`,
`walking_sessions`, and `activity_processing_state`. The processing-state row is updated
in the same transaction as activity records so a sensor counter value cannot be applied
twice after a retry or process restart.

Future versions must provide and test an explicit Room `Migration`. Release builds must
not call `fallbackToDestructiveMigration`, delete the database on startup, or silently
discard inconsistent history. A debug-only reset control may be added separately.

Local date, zone id, UTC offset and the source instant are retained. Historical rows are
never rewritten when the device time zone changes. The offset is part of an hourly
bucket's identity so repeated local hours during daylight-saving transitions remain
distinct.
# Schema version 2

Migration 1 to 2 is non-destructive. It persists unclassified-step quality,
profile inputs applied to hourly metrics, walking-session recovery fields, and
active automatic/manual session references. Exported Room schemas `1.json` and
`2.json` are both retained.
The dedicated `Migration1To2PreservesActivityDataTest` creates a real version 1
database, inserts hourly, daily, session, and processing-state fixtures, and opens it
as version 2 with schema validation. Existing hourly rows receive immutable version 1
assumptions: 0.70 m step length, 60 kg applied weight, and formula version 1.
Daily `unclassifiedStepsQuality` is `UNKNOWN` when the count is zero and `RECOVERED`
when it is positive. These migration values are historical evidence and are not
rewritten by later profile changes.
# Version 3

`tracking_gap_records` は欠測区間、理由、状態、回復歩数、品質、origin、監査時刻を保存する。
`processed_external_step_records` は外部 record ID と fingerprint、適用歩数を保存し冪等性を担保する。
Migration 2→3 は既存の時間別、日次、セッション、processing state を変更せず新規表だけを作る。

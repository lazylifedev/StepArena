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

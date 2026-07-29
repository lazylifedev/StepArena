# Hourly aggregation

`TYPE_STEP_COUNTER` is authoritative. When detector timestamps are present, they locate
up to the counter delta in exact hourly buckets; any remainder stays within the counter
interval. Detector counts exceeding the counter delta are ignored for totals.

Without detector timestamps, a same-hour delta stays in that hour. A cross-hour delta is
distributed by elapsed milliseconds, with integer remainder assigned once to the last
bucket. The allocation therefore always sums exactly to the counter delta.

Every bucket stores its instant range, local date, hour, zone id and UTC offset. Daily
records are rebuilt from hourly rows, not incremented independently, which makes rebuild
and retry idempotent. A two-hour-or-longer gap is marked recovered rather than pretending
the last observed hour was precisely measured.
# Phase 3.1 time-zone and DST behavior

Daily and hourly reads are keyed by both local date and stored zone ID. Hourly
buckets are ordered by `periodStartEpochMillis`, not collapsed by hour number.
Therefore a DST start day may contain 23 buckets and a DST end day 25; repeated
local hours are shown with their UTC offset.

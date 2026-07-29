# Data Reliability

## 信頼性区分

- `COMPLETE`: 選択したソースで期間を完全に観測
- `PARTLY_ESTIMATED`: 欠測を推定値で表示
- `PARTLY_RECOVERED`: 再取得または別ソースで補完
- `NO_DATA`: 信頼できる値がない

オフラインは信頼性と別軸である。端末内データが完全でもオフラインになり得るため、1つの Boolean に統合しない。画面は推定・補完・オフラインを色だけでなく文言でも示す。

負数の歩数、距離、時間、カロリー、速度は表示前のドメイン関数で0へ正規化する。複数ソースの重複、センサーリセット、端末時刻変更、タイムゾーン変更、夏時間、日付境界を監査可能なメタデータとともに扱う。

将来の DB は event timestamp を `Instant`、集計キーを `LocalDate`、集計時の zone ID を IANA 名で保存できる設計とする。端末時刻だけを真実の時刻として扱わない。
# Phase 2 step reliability

Confirmed daily steps survive sensor reset and boot-session changes. Before a
local date or zone boundary resets today's counter, a `DailyStepSummary` is
saved through a repository boundary. This is interim single-summary storage;
Room-backed history is deferred. A heartbeat independent of sensor events
distinguishes no walking from a stopped service.

# Tracking Gap Recovery

Heartbeat は通常 5 分間隔、12 分で stale、30 分で停止疑いとする。遅延だけで停止と断定せず、
WorkManager の一意名 `tracking-health-monitor` で 15 分以上の遅延可能な補助監視を行う。

ユーザーの明示停止区間は既定で補完しない。gap は fingerprint により冪等に作成する。
補完優先順位は自前 Step Counter、信頼可能な外部歩数、Counter 差分、不明の順である。
外部日次合計は直接加算せず、区間を切り詰め、自前確定分と適用済み外部分を控除する。
粗い区間や異常値は自動適用せず要確認とする。

WorkManager は状態診断と gap 作成だけを保証範囲とし、正確な時刻、リアルタイム計測、
Foreground Service の無条件再起動を担わない。再開は通知からのユーザー操作を起点とする。

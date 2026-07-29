# Data Export Format

SAFでユーザー指定先へUTF-8 ZIPを書き出す。CSVはRFC 4180相当、日時はISO 8601。`activity_daily.csv`, `activity_hourly.csv`, `walking_sessions.csv`, `tracking_gaps.csv`, `matches.csv`, `leagues.csv`, `seasons.csv`, `achievements.csv`, `settings.json`, `metadata.json`, `README.txt` を含む。

metadataはexportedAt、appVersion、databaseVersion、locale、zoneId、recordCounts、dateRange、healthConnectEnabled、`accountUsed=false`、`serverSyncUsed=false` を記録する。秘密、外部record ID、DataOrigin詳細、内部パス、Debugデータを含めない。空データでもmetadataとREADMEを作る。

Phase 6.1ではCSV escapingとJSON escapingのUnit Testは成功した。SOV41での
SAF保存、11ファイル、UTF-8、実DB件数整合、空データZIPは未確認。

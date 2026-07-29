# Data Deletion

ゲーム初期化はPlayerProfile、試合、リーグ、シーズン、実績、ゲーム通知だけを二段階確認後に削除し、活動記録とプロフィール設定を残す。

全削除はRoom全テーブル、StepArena設定、通知設定、一時エクスポートを対象とし、「削除」の入力を要求する。Health Connect元データは削除しない。`deletion_in_progress` を先に永続化し、次回起動で再開できる構造とする。Service停止、Workキャンセル、通知消去と残留検査は実機QA項目。

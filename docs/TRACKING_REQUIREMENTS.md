# Tracking Requirements

## 状態

計測状態は `ACTIVE`、`NOT_STARTED`、`MAY_BE_STOPPED`、`PERMISSION_REQUIRED`、`BATTERY_SETTING_REQUIRED` を区別する。最後に正常データを確認した `Instant` と、表示時の `ZoneId` を分ける。

## 後続実装の原則

- `TYPE_STEP_COUNTER` と `TYPE_STEP_DETECTOR` の端末差を調査してから採用する。
- センサー値のリセット、再起動、日付変更を raw 値と日次差分で吸収する。
- Foreground Service と常駐通知は OS 要件、電池、ユーザー説明をセットで設計する。
- Health Connect と端末センサーの重複を識別し、無条件に加算しない。
- UI は SensorManager や Service に直接アクセスせず Repository を購読する。

Phase 0・1 の開始ボタンは UI 操作確認のみであり、センサーやサービスを起動しない。

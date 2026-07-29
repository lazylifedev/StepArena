# Release QA Matrix

|領域|組み合わせ|状態|
|---|---|---|
|Android|10, 11(SOV41), 12, 13, 14, 15, 16相当|11実機予定、その他未検証/エミュレーター|
|テーマ|ライト、ダーク、システム|自動/手動確認予定|
|文字|100%, 130%, 150%, 200%|Compose UI/手動確認予定|
|権限|全許可、活動拒否、通知拒否、HC拒否、Providerなし|SOV41/テスト予定|
|計測|初回、再起動、force-stop、画面OFF、Activity終了、省電力、センサーなし|Phase 2証跡＋Phase 6再試験|
|データ|空、大量、Migration 1→5、ZIP、ゲーム初期化、全削除|自動/実機予定|
|ゲーム|WIN/LOSS/DRAW/NO_CONTEST、昇降格、月週境界、30,000歩|既存Unit/Instrumentation|

未検証を合格扱いしない。物理歩行とHealth Connect実Providerは別の公開前ゲート。

## Phase 6.1 status (2026-07-29)

- Android 11 / SOV41: instrumentation 25/25、全件3/3、グループ別3/3成功。
- Landscape: オンボーディングのみ既存確認。Phase 6対象画面一式は未完了。
- TalkBackと200%文字: 未完了。
- Android 10 / 13 / 14 / 15または16: 利用可能なエミュレーター未確認。
- ZIP、ゲーム初期化、全削除、削除復旧: 実機手動確認未完了。

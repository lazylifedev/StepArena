# StepArena

StepArenaは、端末の歩数センサーで日々の活動を記録し、ローカル生成NPCとの毎日の対戦を楽しむAndroidアプリです。アカウント、ログイン、クラウド同期、オンライン対戦、広告はありません。

> スクリーンショットはGoogle Play公開用画像の確定後に掲載します。撮影計画は `docs/STORE_SCREENSHOT_PLAN.md` を参照してください。

## 主な機能

- 常時歩数計測、時間別・日次履歴、手動／自動散歩セッション
- 距離、歩行時間、推定カロリー、速度
- 任意・既定OFFのHealth Connect Steps欠測補完
- ローカルNPCデイリーマッチ、ランク、週間リーグ、シーズン、実績
- 端末内データの使用状況、ZIPエクスポート、ゲーム初期化、全削除
- 計測停止診断とバッテリー設定案内
- TalkBack、動的文字サイズ、Reduce Motionを考慮したCompose UI

対戦相手は実在ユーザーでもAIでもありません。対戦有効歩数は1日30,000歩までですが、通常の歩数履歴は上限後も記録されます。本アプリは医療機器ではありません。

## 技術構成

- Kotlin / Jetpack Compose / Material 3 / Navigation Compose
- Room schema v5 / DataStore / WorkManager
- Health Connect Client
- Foreground Service (`health`)
- Gradle Kotlin DSL / Version Catalog
- JUnit / Room instrumentation / Compose UI Test

対応OSはAndroid 10（API 29）以降、targetSdk 36です。Android 11のSOV41を継続的な実機対象にしています。

## ビルドとテスト

JDK 17とAndroid SDKを用意し、プロジェクトルートで実行します。

```powershell
.\gradlew.bat assembleDebug
.\gradlew.bat assembleRelease
.\gradlew.bat bundleRelease
.\gradlew.bat testDebugUnitTest
.\gradlew.bat lintDebug
.\gradlew.bat lintRelease
```

接続端末では `.\gradlew.bat connectedDebugAndroidTest` を実行します。物理歩行試験とHealth Connect実Provider試験はInstrumentationの代替にならず、公開前ゲートとして別途必要です。

## データとプライバシー

通常データは端末内に保存します。アプリ自身による外部サーバー通信、広告SDK、分析SDKはありません。ユーザーが明示的に選んだ場合だけSAFでZIPを書き出します。Android自動バックアップからRoom、DataStore、設定を除外し、エクスポートを正式なバックアップ手段とします。

Health ConnectはStepsの読取のみで、欠測補完に使用します。元データの削除や権限管理はHealth Connect側で行います。詳細は `docs/PRIVACY_POLICY_JA.md` と `docs/BACKUP_POLICY.md` を参照してください。

## ライセンスと貢献

直接依存するAndroidX、Compose、Material、Room、DataStore、WorkManager、Health Connect Client、Kotlinは各ライセンスに従います。アプリ内にもライセンス案内があります。

変更は小さく検証可能な単位で行い、既存の履歴・実機証跡・未追跡作業を保全してください。force push、rebase、amend、squash merge、履歴書き換えは禁止です。

## 開発状態

Phase 6（Google Play公開準備、データ管理、アクセシビリティ、最終QA）を実施中です。公開前にはRelease AAB監査、SOV41手動QA、物理歩行、Health Connect実Provider、Play Console申告、公開URL／サポート窓口設定、GitHub既定ブランチのmain変更が必要です。

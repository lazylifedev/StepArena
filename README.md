# StepArena

StepArena は、毎日の歩行を1試合として楽しむ Android アプリです。歩数、日次の非同期マッチ、週間リーグ、月間シーズン、ランク、称号、成長記録を通じて、歩き続けたくなる体験を目指します。

現在は Phase 0・Phase 1 です。実センサー、Health Connect、GPS、常駐サービス、サーバー通信は未実装で、ホーム画面には明示的なダミーデータを使用しています。

## 技術構成

- Kotlin / Jetpack Compose / Material 3
- Single Activity / Navigation Compose
- Coroutines / Flow / StateFlow
- Gradle Kotlin DSL / Version Catalog
- JUnit / Compose UI Test
- Android 10（API 29）以降

Room、DataStore、Hilt、kotlinx.serialization は後続フェーズで実際の用途が生じた時点で導入します。現段階では不要なランタイムやコード生成を追加していません。モーション設定には、将来 DataStore 実装へ交換できる Repository 契約があります。

## ビルドと実行

JDK 17 と Android SDK を用意し、プロジェクトルートで実行します。

```powershell
.\gradlew.bat assembleDebug
.\gradlew.bat testDebugUnitTest
.\gradlew.bat lintDebug
```

Android Studio で `app` 構成を選択し、Android 10 以降の端末またはエミュレーターへ実行できます。

## Debug UI評価

Debugビルドのホーム画面で端末の音量下キーを長押しすると、UI評価シートが開きます。計測状態、データ信頼性、オフライン、目標・対戦・昇格状態と、Full / Reduced / Offの各モーションを実データなしで切り替えられます。この機能と評価データは`app/src/debug`だけにあり、Releaseビルドには含まれません。

実機レビュー画像は`screenshots/phase1/`に保存します。通知内容や端末固有IDなどの個人情報を含めないでください。

## ディレクトリ

```text
app/src/main/java/com/lazyapps/steparena/
├─ app/                 Activity、Application、Navigation
├─ core/
│  ├─ designsystem/     Pulse Arena のトークン、部品、モーション
│  ├─ model/            表示に依存しないモデル
│  ├─ time/             タイムゾーン対応 Formatter
│  └─ units/            単位変換、数値正規化、Formatter
└─ feature/
   ├─ home/             UDF、ViewModel、Repository、ホーム UI
   └─ placeholder/      未実装画面の安全な遷移先

docs/                   要件・アーキテクチャ・信頼性・開発計画
design/                 デザイン原則・トークン・モーション・アクセシビリティ
```

Preview 専用データは `app/src/debug`、テストデータはテストソースセットに置き、本番 APK のモデルへ混入させません。

## 今後

センサー調査、権限 UX、DataStore による設定永続化、Room による集計、Foreground Service、Health Connect、実マッチングの順に段階導入します。詳細は [DEVELOPMENT_PHASES.md](docs/DEVELOPMENT_PHASES.md) を参照してください。

## Git 運用

force push、rebase、amend、squash merge、履歴書き換えを行いません。作業ブランチを通常コミットし、検証後に `main` へ通常マージして push します。ユーザーが配置した未追跡ファイルやデザイン素材は明示的な依頼なく追加・変更・削除しません。

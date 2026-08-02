# Compose Test Stability

## Phase 6.1 investigation (2026-07-29)

SOV41 / Android 11で、25件の連続Instrumentation中に試行ごとに異なる
Composeテストが`No compose hierarchies found`で失敗した。失敗はassertionの
内容ではなく、Composeテスト用`ComponentActivity`のホストが利用可能になる前、
またはホスト消失後にsemanticsへアクセスした場合に発生していた。

Compose画面テストはすべて次の方式へ統一した。

- 各テストメソッドに新しい`ActivityScenario<ComponentActivity>`を生成する
  `createAndroidComposeRule`を使用する。
- `@Before`でScenarioが`RESUMED`になるまで最大10秒待ち、`waitForIdle()`後に
  `setContent`する。
- Scenarioが既に`DESTROYED`なら待ち続けず、状態を含む明確な失敗にする。
- ホストの初期状態と準備完了状態を`StepArenaComposeTest`タグでlogcatへ記録する。
- v2 Compose test APIへ統一し、クラス間でruleやActivityを共有しない。

全データ削除、実`force-stop`、実`am crash`は通常のComposeテストプロセスへ
混在させない。`force-stop`と`am crash`は全Instrumentation終了後の外部ADB手順で
確認する。

## SOV41 repeat results

修正後commit前の作業ツリーで実施。

| Group | Tests per run | Consecutive result |
|---|---:|---:|
| DAO / Migration | 7 | 3/3 pass |
| Tracking | 3 | 3/3 pass |
| Game | 5 | 3/3 pass |
| Phase 6 data-management UI | 2 | 3/3 pass |
| Compose UI | 14 | 3/3 pass |
| Accessibility-related Compose | 8 | 3/3 pass |
| Full instrumentation | 25 | 3/3 pass |

Phase 6 data-management UI groupは画面到達性のみであり、ZIP、ゲーム初期化、
全削除、削除中復旧の実処理合格を意味しない。これらの実機手動ゲートは未完了。

# Game device test results

## 2026-07-29 Phase 5.1

- Device: SOV41
- Android: 11
- App: 1.0.0 (versionCode 1)
- Starting implementation commit: `a643e47`
- Physical walking test: not performed

### Scenario A: basic match

Result: **Fail; rerun required**

Initial state after the first Debug reset:

- Daily activity: 0 records
- Hourly activity: 0 records
- DailyMatch: 0 records
- Rating: 1,000 / Bronze III
- Wins and win streak: 0 / 0
- Duplicate running service: not observed

Operations and database result:

- Created one DailyMatch.
- Added Counter +5,000 exactly once from the zero-step state.
- Set the NPC target to 4,000 steps.
- Finalized the match once.
- Daily steps: 5,000
- DailyMatch: one record, `WIN`
- Eligible steps: 5,000
- Rating: 1,000 -> 1,025
- Wins and current win streak: 1 / 1
- A second finalization attempt did not add another rating change, win, or streak.

UI result:

- The normal Home screen still displayed 0 steps although the Debug daily record contained
  5,000 steps.
- Root cause: the initial Debug clock used `Etc/GMT-9`, while the normal Home path observes
  `ZoneId.systemDefault()`.
- Fix commit: `29c3383`.
- Scenario A must be reset and rerun after the Debug-data isolation issue below is fixed.

### Blocking Debug-data isolation issue

The current Debug operations update the normal `game_player_profile` row. Generated match
notifications also use a normal daily-match source ID rather than a `debug-` source ID.
Consequently, the current Debug reset cannot restore rating/win state or reliably remove all
events while preserving normal user data. Scenarios A-E therefore cannot be isolated safely.

The following initial manual-test blocker was fixed separately in `baea337`:

- Debug reset and maintenance actions were not reachable from the Debug UI.
- Reset immediately recreated a DailyMatch, preventing the required zero-match initial state.
- The required 4,000-step NPC target was not available.

### Unverified items

- Scenario A restart and Settings force-stop persistence after a passing rerun
- Scenarios B-E
- Achievement UI checks
- 30,000-step cap UI checks
- Crash-triggered process recreation checks for Phase 5.1 game data
- Final SOV41 instrumentation/DAO/migration/Compose UI gate
- Final clean/build/release/lint/non-contamination gates
- Main merge and push

## Debug-data isolation implementation (2026-07-29)

過去の不合格および阻害記録は上記のとおり保持する。修正ではDebug source set専用の
`step_arena_debug_game.db`と`step_arena_debug_scenario.preferences_pb`を採用した。
明示的に「隔離シナリオを開始」し確認後にだけ切り替わり、通常画面にも
「隔離テストデータ」バナーを常時表示する。通常データへ戻る際はDebugデータを削除しない。

分離対象:

- Room全テーブル（活動、ゲーム、通知）
- Clock/Zone、installationId、scenario state DataStore
- production/debug Game Maintenance Workerと一意Work名
- 通知channel/group/ID/requestCode/設定/遷移data area
- Fake Counter経路（通常Foreground Serviceを隔離モードでは起動しない）
- Debug reset（Debug DB/DataStore/Clockだけ）

自動化したproduction不変テストは、別DBへrating 1,234、勝数7、歩数321、
DailyMatch/通知/実績各1件を配置し、Debug側rating 1,025・歩数5,000の変更と全reset後も
production識別値が同一であることを検証する。

SOV41 (`QV7209CF25`, Android 11) では全Instrumentation 25件中、新規production
不変テストを含む24件が成功するところまで確認した。新規隔離テストとDebug Compose
5件の単独再実行は5/5成功した。その後の全件再試行は端末package installerが
`DELETE_FAILED_INTERNAL_ERROR`とshell timeoutになり、APK再installも応答しなかったため、
最終25/25とシナリオAの手動再試験は未完了である。

したがってAは合格扱いにせず、B〜Eと物理歩行も未実施のままとする。

## 2026-07-29 Phase 5.1 再試験

SOV41 (`QV7209CF25`, Android 11) のインストール停止原因は、Google Play Protectの
「セキュリティ診断のためにアプリを送信しますか？」ダイアログが未回答のまま
前面に残っていたことだった。「送信しない」を選択後、Debug APK
（40,681,584 bytes）とandroidTest APK（3,212,079 bytes）はADB直接インストールに成功した。
Package Installerのデータ消去、端末再起動、通常アプリデータ削除は行っていない。

`connectedDebugAndroidTest` は初回24/25で、Composeホストを一時的に取得できなかった
`HomeScreenTest.bottomNavigation_opensLocalMatch` だけが失敗した。同テストの単独再実行は
1/1成功し、全件再実行も25/25成功した。FATAL/ANRはなかった。

手動シナリオ開始時、Debug初期化がMain thread上の`clearAllTables()`でクラッシュする問題を
検出し、IO dispatcherへ移した。さらにCounterが時間別DBへ反映されない問題、モード切替後に
保持されたHomeViewModelが切替前graphを参照する問題、Silver境界用Debug値とラベルの不一致を
修正した。

- 通常識別値: rating 1,000、勝数0、歩数0、DailyMatch 1、通知0、実績0
- A: 日次5,000、時間別5,000、DailyMatch 1、WIN、rating 1,000→1,025、勝数1、連勝1
- A: 再確定後不変、通常再起動後維持、force-stop後維持、通常識別値は全項目不変
- B: RECOVERED 1,000に対し有効800、制限200、除外0、`RECOVERED_LIMITED`
- C: 1,600でSilver III、1,599でBronze I
- D: 2026-07-30、2026-08-06、2026-09-06の日次境界、週・月境界の確定／次期間生成
- E: 対戦結果1件、実績2件、合計3件。同日再処理2回後もdeduplication keyは3/3で重複なし
- A〜E終了後の通常識別値は開始時と同一
- 物理歩行は未実施

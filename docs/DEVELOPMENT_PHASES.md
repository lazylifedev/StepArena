# Development Phases

## Phase 0・1（現在）

プロジェクト、UDF、国際化・単位、Pulse Arena デザインシステム、ダミーホーム、モーション3段階、Preview、基本テスト、設計文書。

## Phase 2

オンボーディング、Activity Recognition 権限 UX、センサー能力調査、DataStore による表示・単位・モーション設定の永続化。

## Phase 3

Foreground Service と通知、センサー集計、Room、再起動・日付変更・タイムゾーン変更への耐性、データ信頼性表示。

## Phase 4

Health Connect 統合、重複排除、記録画面、歩行セッション。

## Phase 5

認証、バックエンド、実マッチング、週間リーグ、月間シーズン。不正対策とプライバシー評価をリリース条件に含める。

各フェーズは前フェーズのテスト、lint、実機検証を通過してから開始する。
# Phase 2

Introduces onboarding, permission/battery/sensor diagnostics, real step-counter
collection, a health foreground service, persistent recovery state, daily
rollover boundary, heartbeat, ongoing notification, home binding, and debug
fault states. Hourly Room history, distance, duration, calories, speed, Health
Connect, WorkManager monitoring and real competition remain later phases.
# Phase 3

Phase 3 adds Room-backed hourly/daily activity, walking sessions, five home metrics,
records/profile UI, quality metadata, and deterministic estimation strategies. GPS,
Health Connect, online ranking, and physical-walking acceptance remain later/publication
gates.
# Phase 3.1

Corrects sensor time, walking duration, session identity/timeouts, Room v2,
time-zone/DST reads, quality retention, settings navigation, and localization.
Daily, weekly, and monthly record periods remain disabled for a later phase.
## Phase 3.2 completion

Phase 3.2 separates continuous tracking from manual walking, adds manual UUID
lifecycle/UI/notification actions, automatic-to-manual priority, midnight splitting,
process restoration through Room, stale-action rejection, and a dedicated Room 1 to 2
migration test. Physical walking validation remains an explicit pre-release gate.
# Phase 4

任意の Health Connect Steps 読取、欠測 gap の永続化、外部 record の冪等管理、
Heartbeat の段階判定、一意 Periodic Work、停止通知、設定・診断・補完履歴を追加する。
Health Connect 書込み、距離・ExerciseSession 統合、自動 Service 再起動は対象外とする。
# Phase 5

ローカルNPCデイリー対戦、ランク、週間リーグ、月間シーズン、実績、Room v4、ゲーム画面を実装する。オンライン対戦、アカウント、課金、物理歩行の公開前ゲートは対象外または継続課題。
# Phase 5.1

Debug実機検証UI、ゲーム通知、週間リーグ/月間シーズン確定、初期実績、
公開前の冪等性と表示整合性を対象とする。物理歩行試験は未完了のまま次の実機作業へ残す。

Debug実機シナリオはproductionデータから完全隔離する。独立DB/DataStore、graph切替、
Worker/通知/Fake Counter/reset分離とproduction不変Instrumentation TestをシナリオAの
再開条件とする。Aが合格するまでB〜Eは開始しない。

# Phase 6

Google Play公開準備として、5ページのオンボーディング、利用直前の権限説明、カテゴリ化した設定、
データ使用状況、SAF ZIPエクスポート、ゲームのみ初期化、全削除、アプリ内Privacy/免責/ライセンス、
バックアップ除外、アクセシビリティ、Play申告草案とRelease QAを対象とする。

Roomは既存テーブルで実現できるためschema v5を維持する。物理歩行、Health Connect実Provider、
Play Console最終入力、公開URLとサポート窓口、GitHub既定ブランチ変更は外部の公開前ゲートとして残す。

# Phase 6.1

Compose UIテストをテストごとのActivityScenarioへ分離し、RESUMED確認後に
Compose contentを設定する。SOV41でグループ別および全Instrumentationを3回連続
検証する。ZIP、ゲーム初期化、全削除、削除復旧、TalkBack、200%文字、
force-stop、am crashは実機手動ゲートとして、自動試験と分離して完了判定する。

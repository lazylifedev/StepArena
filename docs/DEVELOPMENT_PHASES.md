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

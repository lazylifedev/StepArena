# Architecture

## 方針

app 単一モジュール内で責務をパッケージ分離し、必要になる前のマルチモジュール化を避ける。画面は単方向データフローで構成する。

```text
Compose UI
  └─ HomeAction
      └─ HomeViewModel
          ├─ HomeRepository interface
          └─ MotionSettingsRepository interface
              └─ data source（後続フェーズ）
```

`HomeViewModel` は不変の `HomeUiState` を `StateFlow` として公開する。状態は `HomeContent`、`TrackingStatus`、`DataReliability`、`MatchOutcome` など意味のある型で表し、Boolean の組み合わせを避ける。

## 境界

Compose は SensorManager、DAO、DataStore、Health Connect、ネットワーク、Service を直接参照しない。現在の `DemoHomeRepository` と `InMemoryMotionSettingsRepository` は、将来の実装と同じ契約を満たすが、永続化や実計測を装わない。

## 将来パッケージ

必要になった時点で `core/database`、`datastore`、`health`、`sensor`、`network`、`notification`、`service/tracking` を追加する。未使用の空パッケージは作らない。DI は依存が増え、手動構築が保守上の負担になったフェーズで Hilt を導入する。
# Phase 2 tracking boundary

`StepTrackingService` owns foreground notification, sensor registration,
heartbeat and persistence timing. `StepCounter` is a pure state transition
component. `TrackingStateRepository` owns DataStore encoding. Compose consumes
repository state through `HomeViewModel`; navigation and ranking logic do not
run in the service.
# Phase 3 activity history

Room is the durable history store behind `ActivityRepository`; UI and the tracking
service do not call DAOs directly. DataStore remains responsible for lightweight
tracking state, sensor baseline, heartbeat, onboarding, and local profile settings.
`ActivityRepository` serializes writes with a mutex and uses one Room transaction for
hourly, daily, session and last-processed sensor state updates.
# Phase 3.1 activity components

`SensorEventClock` converts elapsed-realtime sensor timestamps. Pure
`WalkingDurationCalculator` and `UserBodyProfileValidator` logic is separated
from repository orchestration. `ActivityRepository` remains the Room transaction
boundary and restores active session identity from persisted state.
Phase 3.2 keeps service lifecycle in `StepTrackingService` and walking-session
transactions in `ActivityRepository`. Home observes `trackingRequested` and the
Room-backed active manual session independently. Notification start/end commands are
service intents, while UUID validation and automatic/manual priority are enforced by
the repository.
# Phase 4 boundary

UI、Room、Foreground Service は Health Connect Client を直接呼ばない。
`ExternalActivityDataSource` を境界とし、実 Provider、No-op、Fake 実装を差し替える。
`GapRecoveryRepository` が gap と処理済み外部レコードのトランザクションを所有する。
WorkManager は遅延可能な補助監視であり、常時計測 Service の代替ではない。
# Phase 5 game boundary

Compose UI → `GameRepository` → `LocalGameRepository` → Room DAO の方向に依存する。NPC生成、rating、対戦有効歩数はUI/Roomから独立した純粋なドメイン処理であり、将来`RemoteGameRepository`または`HybridGameRepository`へ差し替えられる。
# Phase 5.1 ゲーム保守

アプリ起動と一意な定期Workは同じ冪等な `runMaintenance` を呼ぶ。
試合、リーグ、シーズン、実績、通知候補はRoomを正本とし、UIはFlowから表示モデルを組み立てる。
Debug操作画面とシナリオ実行器はdebug source setだけに置く。

## Phase 5.1 Debugデータ隔離

`AppGraph` が Room、活動Repository、ゲームRepository、Clock、installationIdを一つの
データ領域として提供する。Releaseはproduction graphだけを持つ。Debugは明示的な
`DebugDataMode`によりproduction graphまたはisolated scenario graphの一方だけを公開し、
Activity再生成後の全画面が同じgraphを取得する。

隔離graphは別Room DB、別DataStore、Debug Clock/Zone、固定Debug installationIdを使用する。
通常Foreground Serviceは隔離モードで起動せず、Fake CounterはDebug DBへの直接シナリオ
入力だけを使う。production Workerはproduction Repository、Debug WorkerはDebug Repository
に固定され、実行時のモード推測でDBを選ばない。

## Phase 6 release layer

`release/DataManagement.kt`が既存Room v5の件数集計、snapshotエクスポート、ゲーム領域削除、
全削除復旧フラグを担当する。Compose設定ルートはSAFを起動するだけで、ネットワーク送信を行わない。
公開情報はアプリ内本文を常に表示し、未設定の外部URLやサポート先をRelease UIへ出さない。

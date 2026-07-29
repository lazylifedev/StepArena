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

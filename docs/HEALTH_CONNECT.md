# Health Connect

Health Connect は任意の欠測補完機能であり、StepArena の Step Counter 計測が正本である。
Phase 4 は `StepsRecord` の読取だけを実装し、書込み、距離、運動セッションは行わない。

利用可能性は `AVAILABLE`、`UPDATE_REQUIRED`、`PROVIDER_NOT_INSTALLED`、
`NOT_SUPPORTED`、`UNKNOWN` に分類する。Android 14 以降のシステム統合型と、
Android 13 以下の Provider アプリ型を SDK の状態 API で判定する。

必要権限は `READ_STEPS` だけである。利用目的を表示してユーザーが有効化し、
拒否後も通常計測を継続できる。WorkManager から Health Connect を背景読取しないため、
背景健康データ権限は要求しない。

取得レコードは origin、record ID、更新日時、区間を保持する。StepArena 自身の origin を
除外し、record ID または fingerprint で二重適用を防止する。Android の端末内歩数は
`android` または Health Connect の synthetic package として分類し、固定の SPN は仮定しない。

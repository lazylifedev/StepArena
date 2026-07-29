# Motion Guidelines

## レベル

- Full: カウントアップ、進捗、カード登場、展開、画面遷移を標準品質で実行
- Reduced: 短いフェードと短時間の値更新に抑える
- Off: アプリ独自の遷移時間を0にし、状態を即時反映

Compose のアニメーションは Android の Animator duration scale に従う。無限アニメーション、背景の高頻度再描画、大量 Blur、複雑な Canvas を使わない。値、進捗、遷移は状態変化時だけ動かす。

現在の切り替えは Preview とホーム下部のデバッグ操作で確認できるが永続化しない。`MotionSettingsRepository` を DataStore 実装へ置き換える Phase 2 で保存を提供する。省電力時は Reduced または Off を提案できる構造にする。

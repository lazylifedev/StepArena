# Phase 7.3 全公開画面 UI 監査

## 監査基準

- 通常画面は主タイトルと主数値を各1つに絞り、状態は短いラベルにする。
- 条件、方式、品質、UTC、センサー内部値は情報／詳細 Sheet または診断画面へ置く。
- 法的文章、利用規約、プライバシー本文はこの文字量監査の対象外とする。
- 200%文字、Landscape、TalkBack semantics、Light/Dark を Compose の構造と自動テストで確認する。

## 画面別分類

| 画面 | 常時必要 | 数字／アイコン | Sheet／詳細へ移動 | 診断へ限定・削除 |
|---|---|---|---|---|
| Home | 今日の歩数、目標、計測状態 | 主要4指標 | Health Connect とカード説明 | raw sensor、session ID |
| Challenge | 対象歩数、相手目標、進捗 | 対戦カード、進捗リング | 対象／制限歩数の説明 | classifier 内部値 |
| Rank | 徽章、現在ランク、次まで | 徽章、進捗バー | 勝敗、連勝、初心者保護 | rating 計算内訳 |
| Weekly Group | 期間、順位、ポイント、上位／自分周辺 | アバター、順位 | ローカル生成、ポイント／順位規則 | participants JSON |
| Monthly Record | 月間歩数、平均、最高日 | 日別バー | なし | raw season payload |
| Records hourly | 指標、24時間グラフ | 棒グラフ | UTC、品質、全指標 | 前後大ボタンを削除 |
| Records daily | 主数値、目標 | 指標 chip | 品質、集計元 | DataQuality enum |
| Records session | 時刻、歩数、距離、時間 | セッション行 | 開始／終了、品質、速度 | session ID |
| Achievements | 名称、実進捗 | 2列 badge、鍵／チェック、新着 dot | 条件、進捗、達成日 | 0固定進捗を削除 |
| Settings | アイコン、項目名、現在値、chevron | アイコン | 説明は遷移先 | 診断値は診断画面のみ |
| Profile | 表示名、身長、体重、歩幅 | 情報アイコン | 推定式、履歴適用方針 | 内部 player ID |
| Health Connect | 有効状態、権限状態 | switch、状態ラベル | 補完規則 | origin package 等 |
| Diagnostics | 計測・通知・永続化の詳細 | 状態カード | 該当なし | 公開通常画面から隔離 |
| Onboarding | 1画面1メッセージ | 大きなアイコン、進捗 | 詳細は設定／情報画面 | 長文を削除 |
| Empty/Error | 次にできる操作、短い理由 | 状態アイコン | 詳細が必要な場合のみ | stack trace、raw key |
| 通知 | 正式歩数＋一時 preview、状態 | 小アイコン | アプリ内の詳細画面 | pending は非永続・非競技 |

## 自動確認

- ナビゲーション: bottom destination と Arena 内タブを分離し、旧 route を canonical route へ変換するテスト。
- Source of Truth: 正式 Counter、通知 preview、Challenge 対象歩数を別モデルにし、確定前後と過去日の回帰テスト。
- 画面: Home、Challenge、Rank、Weekly Group、Monthly Record、Records、Achievements、Profile、Health Connect、Onboarding の Compose instrumentation。
- アクセシビリティ: chart／progress の `contentDescription`、クリックラベル、48dp操作領域、200%文字時の1列化。
- 表示環境: 200%文字＋Landscape、Light theme の代表画面テスト。通常の instrumentation は Dark theme を使用する。
- 静的監査: 公開画面から raw sensor、session ID、enum／JSON key を除き、診断画面へ限定した。

## 完了判定

実機スクリーンショットを完了条件にはしない。上記のコード構造、画面別テスト、JVMテスト、lint、debug/release APK、release AAB、`git diff --check` を必須ゲートとする。

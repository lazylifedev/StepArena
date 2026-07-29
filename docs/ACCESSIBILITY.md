# Accessibility

- 主要操作は48dp以上、アイコン操作にはcontentDescription、見出しsemanticsを付ける。
- 状態は色だけでなくラベル・形状で表す。グラフは全体要約と必要時の詳細を提供する。
- TalkBack順序、選択状態、進捗、エラー、ダイアログタイトルとフォーカスを確認する。
- 100/130/150/200%、320/360/411/600dp、Landscape、分割画面を確認する。
- Reduce Motionでは遷移を簡略化し、点滅・無限アニメーションを使わない。
- 自動semantics検査だけで合格とせず、SOV41でTalkBackを手動確認する。

## Phase 6.1

Accessibility関連Composeテスト8件はSOV41で3/3成功した。これはsemanticsと
画面到達性の自動確認であり、TalkBack手動確認、200%文字、全対象画面のLandscape
確認を代替しない。これら3項目は未完了。

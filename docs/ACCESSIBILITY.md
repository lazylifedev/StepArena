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

## Phase 6.6 グラフ要約

グラフ全体の semantics は、歩数・距離・時間・カロリーについて合計と最大時間帯を伝える。速度は最速時間帯のみを伝える。null は0として最大値判定せず、個別棒の時刻、値、単位、品質、UTC offset、選択状態、操作ラベルは維持する。最終的な文言と読み上げ順は SOV41 の TalkBack で手動確認する。

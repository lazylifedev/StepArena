# Google Play Data Safety 回答草案

現行 `1.0.0` のコード監査に基づく草案。Play Consoleの最新定義を確認して最終入力する。

- アプリ外へのデータ収集・共有: なし。アカウント、サーバー、広告、分析、クラッシュ収集SDKなし。
- Health and fitness: 歩数・身体活動を端末内処理。Health ConnectのSteps読取は任意・既定OFF。
- Personal info: 身長・体重は任意入力、端末内の距離・カロリー計算のみ。
- App activity: ローカルNPCゲーム進行を端末内保存。
- Diagnostics: 計測状態・エラーコードを端末内保存。共有はユーザーの明示操作のみ。
- Device identifiers: Android ID、広告ID、端末シリアルを取得しない。サーバー送信するinstallationIdなし。
- Files and docs: SAFによるユーザー指定ZIP書き出しのみ。
- Crash logs: 収集なし。
- 削除: 設定 → データ管理 → 全データを削除。アンインストールでも削除。

Playの「収集」は通常、端末外への送信を指す。端末内処理とユーザー起動のエクスポートを最終フォームの注記と整合させる。

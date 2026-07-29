# デイリーマッチ

現在のZoneIdにおけるLocalDateごとに通常試合を一件だけ作る。SeedはseasonId、localDate、rank、個人情報を含まないinstallationIdから作る。同じ入力ではNPCと目標が同一になる。

前日試合の確定、rating反映、連勝更新は単一Room transactionで行う。確定済みまたはrating適用済みの試合は再処理しない。0歩同士、異常検知日はNO_CONTESTとする。
# Phase 5.1

試合確定はRoomトランザクション内で行い、`status` と `ratingAfter` を冪等性キーとして
再確定による二重加算を防止する。結果通知候補は試合ID単位で一件だけ作成する。

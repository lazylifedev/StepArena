# Git Rules

- 作業前後に status、branch、log、remote、ahead/behind を確認する。
- 通常の作業ブランチと通常コミットを使用する。
- force push、rebase、amend、squash merge、履歴書き換えを禁止する。
- `main` への統合は通常マージとし、検証成功後に `origin/main` へ push する。
- IDE 設定、ローカル SDK 設定、ビルド成果物をコミットしない。
- ユーザーが置いた未追跡ファイルとデザイン素材は、明示的な依頼なく追加・変更・削除しない。
- stage は意図したファイルを列挙し、`git add .` を使わない。
- commit 前に `git diff --check` と変更ファイル一覧を確認する。

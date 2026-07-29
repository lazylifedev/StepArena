# Release Checklist

- [ ] Unit / DAO / Migration / Instrumentation / Compose UI
- [ ] assembleDebug / assembleRelease / bundleRelease
- [ ] lintDebug / lintRelease / git diff --check
- [ ] Release Manifest、DEX、AABのDebug/Fake/秘密非混入
- [ ] オンボーディング、権限拒否と再案内
- [ ] ZIP内容とsnapshot整合
- [ ] ゲーム初期化、全削除、削除中復旧
- [ ] TalkBack、200%、320dp、600dp、Landscape、ライト/ダーク
- [ ] force-stop、am crash、通知、Worker残留
- [ ] Data Safety / Health Apps / FGS / Privacy / Store文面を実装と再照合
- [ ] SOV41物理歩行、Health Connect実Provider
- [ ] GitHub既定ブランチをmainへ手動変更（確認前にmasterを削除しない）

## Phase 6.1 evidence

- [x] Unit 94/94、SOV41 Instrumentation 25/25
- [x] Instrumentation全件3/3、6グループ各3/3
- [x] assembleDebug / assembleRelease / bundleRelease
- [x] lintDebug / lintRelease / git diff --check
- [ ] SOV41 ZIP、ゲーム初期化、全削除、削除中復旧
- [ ] SOV41 TalkBack、200%文字、Phase 6 Landscape
- [ ] SOV41 force-stop、am crash Phase 6再試験
- [ ] 物理歩行、Health Connect実Provider
- [ ] 正式upload key設定と正式署名AAB
- [ ] Privacy Policy公開URL、サポート窓口

正式署名AABの生成は、upload key設定後に実施する。
現在のAABは公開提出用署名確認前のビルド成果物。
未完了公開ゲートがあるためGoogle Play本番アップロード、main統合、pushは行わない。

## Phase 6.6

- [x] グラフ要約Presentation ModelとUnit Test
- [x] 通知本文・操作ラベル・チャネル名／説明のリソース化
- [x] Records単位表示を既存ActivityFormatterへ統一
- [x] 公開UIのLocale.JAPAN / Locale.JAPANESE固定なし
- [x] Unit 114/114、Records UI 3/3、SOV41全Instrumentation 28/28
- [x] assembleDebug / assembleRelease / bundleRelease、lint Error 0、git diff --check
- [x] Release APK / AABエントリとRelease ManifestのDebug/Fake識別子0件
- [ ] SOV41 TalkBackでグラフ全体・個別棒を確認
- [ ] Android設定で既存通知チャネルの新名称を確認

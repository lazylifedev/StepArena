# ゲーム通知

ゲーム通知は計測通知と分離した `game_results` チャネルを使う。設定初期値はOFFで、
ユーザーがONにした場合だけ配信する。

通知候補は対戦結果、昇格、実績解除、週間リーグ確定、シーズン確定。
Roomの `game_notification_events.deduplicationKey` に一意制約を置き、
WorkManager、アプリ起動、再処理が重なっても一件だけ配信する。

22:00〜8:00は静かな時間帯とし、候補の `notBeforeEpochMillis` を翌朝8時へ送る。
通知タップは対象画面へ遷移し、対象データが消えていても各画面の空状態を表示する。
PendingIntentのrequest codeは通知イベントIDから生成する。

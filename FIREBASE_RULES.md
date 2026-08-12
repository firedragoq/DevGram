# Firebase rules

Файл `database.rules.json` соответствует текущему клиенту DevGram: клиент передаёт Telegram ID в `submitterId`, `userId` и `reporterId`, а Firebase Auth используется только модераторами.

Публикация:

```bash
firebase use devgram-d03e4
firebase deploy --only database
```

Текущий клиентский протокол ещё не поддерживает Anonymous Auth, поэтому запись заявок, отзывов и жалоб временно валидируется по структуре и размеру, но не по `auth.uid`. Для полного антиспама нужно сначала перевести APK на Firebase Anonymous Auth, после чего заменить `.write: true` в этих трёх узлах на проверку `auth.uid` и вынести лимиты/автоскрытие в Cloud Functions.

Никогда не добавляй Telegram Bot API token в rules, APK или репозиторий. Для уведомлений используй Secret Manager в Cloud Functions.

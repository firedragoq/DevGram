# Cloud Functions

Установка и публикация выполняются на ПК из корня проекта:

```bash
cd TelegramAndroid/functions
npm install
npm run lint
cd ..
firebase functions:secrets:set DEVGRAM_TELEGRAM_BOT_TOKEN
firebase deploy --only functions
```

Функции ограничивают отзывы и жалобы по дневному лимиту, пересчитывают рейтинг, скрывают плагин после пяти жалоб и уведомляют модераторов. Токен Telegram вводится интерактивно в Secret Manager и не попадает в Git.

Текущий APK передаёт Telegram ID, поэтому лимиты являются переходными. Для защиты от подмены ID следующим шагом нужно включить Firebase Anonymous Auth в APK и передавать `auth.uid` в `submitterUid`/`reporterUid`; после миграции правила можно ужесточить без открытой записи.

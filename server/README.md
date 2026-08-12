# DevGram Catalog Guard через PM2

## 1. Создать конфигурацию

```bash
cd /home/TelegramAndroid/server
cp catalog_guard.env.example catalog_guard.env
nano catalog_guard.env
```

Заполняются следующие значения:

- `DEVGRAM_FIREBASE_API_KEY` — Firebase Console → Project settings → General → Your apps → Web API Key. В текущем Android-коде используется Web API key проекта `devgram-d03e4`.
- `DEVGRAM_FIREBASE_EMAIL` — email отдельного аккаунта модератора из Firebase Authentication → Users.
- `DEVGRAM_FIREBASE_PASSWORD` — пароль этого аккаунта модератора.
- `DEVGRAM_TELEGRAM_BOT_TOKEN` — новый токен от `@BotFather`, команда `/token`. Старый раскрытый токен нужно отозвать через `/revoke`.
- `DEVGRAM_OWNER_CHAT_ID` — Telegram ID группы модераторов, сейчас `-5450213229`.

Права аккаунта модератора: его Firebase UID должен присутствовать в Realtime Database по пути `/moderators/{uid}`.

## 2. Проверить вручную

```bash
set -a
source /home/TelegramAndroid/server/catalog_guard.env
set +a
python3 /home/TelegramAndroid/server/catalog_guard.py
```

После строки `DevGram Catalog Guard started` остановить проверку через `Ctrl+C`.

## 3. Запустить через PM2

```bash
cd /home/TelegramAndroid/server
pm2 start ecosystem.config.cjs
pm2 save
pm2 startup
```

Команда `pm2 startup` напечатает одну команду с `sudo`; её нужно скопировать и выполнить, затем ещё раз выполнить `pm2 save`.

Проверка:

```bash
pm2 status
pm2 logs devgram-catalog-guard
```

Перезапуск после изменения `catalog_guard.env`:

```bash
pm2 restart devgram-catalog-guard --update-env
```

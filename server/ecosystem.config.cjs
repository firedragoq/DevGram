const legacy = require("/home/statbot/ecosystem.config.js").apps[0].env;
module.exports = {
  apps: [{
    name: "devgram-catalog-guard",
    script: "catalog_guard.py",
    cwd: "/home/TelegramAndroid/server",
    interpreter: "/usr/bin/python3",
    env_file: "/home/TelegramAndroid/server/catalog_guard.env",
    env: {
      DEVGRAM_TELEGRAM_BOT_TOKEN: legacy.STATBOT_TOKEN,
      DEVGRAM_OWNER_CHAT_ID: legacy.STATBOT_CHAT
    },
    autorestart: true,
    restart_delay: 10000,
    max_restarts: 20,
    min_uptime: "15s",
    time: true,
    merge_logs: true,
    out_file: "/home/TelegramAndroid/server/catalog_guard.log",
    error_file: "/home/TelegramAndroid/server/catalog_guard-error.log"
  }]
};

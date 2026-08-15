#!/usr/bin/env bash
set -euo pipefail

command -v adb >/dev/null || { echo "adb не найден: установите Android SDK Platform Tools" >&2; exit 1; }
command -v python3 >/dev/null || { echo "python3 не найден" >&2; exit 1; }

read -r -s -p "Вставьте токен Dev Server из DevGram: " DEVGRAM_TOKEN
echo
export DEVGRAM_TOKEN

adb devices
python3 ./devgram_dev.py status
python3 ./devgram_dev.py upload ./DevGram-DevServerDemo-1.0.0.dgplugin
echo "Готово. Проверьте DevGram → Плагины."

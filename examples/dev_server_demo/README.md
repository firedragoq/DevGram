# Dev Server Demo

Безопасный тестовый плагин DevGram. Он показывает нативные bulletins,
хранит счётчик hot reload, добавляет экран настроек и пунк в меню сообщения.

## 1. Подготовить DevGram

1. Установите актуальную release-сборку DevGram 12.9.3.
2. Откройте `DevGram → Плагины → ⓘ Система плагинов`.
3. Включите `Режим разработчика`.
4. Откройте `Токен dev server` и скопируйте токен. Порт Dev Server — `42690`.

Не публикуйте токен. После теста его можно сменить кнопкой `Сбросить`.

## 2. Подготовить компьютер

Установите:

- Python 3;
- Android SDK Platform Tools (`adb`).

На Windows распакуйте Platform Tools и добавьте папку с `adb.exe` в `PATH`
либо откройте PowerShell прямо в этой папке.

## 3. Подключить телефон по USB

1. Включите на телефоне `Для разработчиков → Отладка по USB`.
2. Подключите кабель и разрешите RSA-отладку в окне на телефоне.
3. Проверьте:

```bash
adb devices
```

В списке должна быть строка со статусом `device`, а не `unauthorized`.

## 4. Загрузить плагин

### Windows

Откройте PowerShell в папке архива и запустите:

```powershell
.\upload-windows.ps1
```

Или вручную:

```powershell
$env:DEVGRAM_TOKEN="токен_из_приложения"
python .\devgram_dev.py status
python .\devgram_dev.py upload .\DevGram-DevServerDemo-1.0.0.dgplugin
```

### Linux/macOS

```bash
chmod +x upload-linux.sh
./upload-linux.sh
```

Или вручную:

```bash
export DEVGRAM_TOKEN="токен_из_приложения"
python3 ./devgram_dev.py status
python3 ./devgram_dev.py upload ./DevGram-DevServerDemo-1.0.0.dgplugin
```

Успешная проверка выведет `ok`, а загрузка — `installed=true`.
Плагин сразу появится в DevGram без перезапуска приложения.

## Беспроводная отладка

На телефоне откройте `Беспроводная отладка → Сопряжение с помощью кода`, затем:

```bash
adb pair IP_ТЕЛЕФОНА:ПОРТ_СОПРЯЖЕНИЯ
adb connect IP_ТЕЛЕФОНА:ПОРТ_ПОДКЛЮЧЕНИЯ
adb devices
```

Порты сопряжения/подключения выдаёт Android. Это не порт Dev Server `42690`.

## Если не работает

- `adb: command not found` — установите Platform Tools или добавьте `adb` в `PATH`.
- `unauthorized` — разблокируйте телефон и подтвердите RSA-ключ.
- `no devices` — проверьте USB-кабель, Samsung USB Driver и режим USB.
- `401 Unauthorized` — скопируйте из DevGram актуальный токен.
- `Connection refused` — включите в DevGram `Режим разработчика`.

## Содержимое

- `main.py` — исходный код плагина;
- `manifest.json` — метаданные пакета;
- `DevGram-DevServerDemo-1.0.0.dgplugin` — готовый пакет;
- `devgram_dev.py` — утилита Dev Server;
- `upload-windows.ps1` и `upload-linux.sh` — быстрый запуск.

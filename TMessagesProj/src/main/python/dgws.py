"""
DevGram: запуск встроенного WebSocket-прокси (порт tg-ws-proxy, Flowseal) внутри приложения.

Прокси поднимается ЛОКАЛЬНО на 127.0.0.1:1443 и заворачивает MTProto в WebSocket прямо
к дата-центрам Telegram — внешний сервер не нужен. Приложение подключается к 127.0.0.1:1443
как к обычному MTProto-прокси (скрытно, через DevGramProxy). Ядро (пакет dgwsproxy) —
чистый stdlib+asyncio, без pip-зависимостей.

──────────────────────────────────────────────────────────────────────────────
 Основано на проекте tg-ws-proxy: https://github.com/Flowseal/tg-ws-proxy (MIT).
 Отдельное спасибо Flowseal за такой божественный обход — WebSocket-бридж прямо
 к DC Telegram, без своего сервера. Респект. 🙏
──────────────────────────────────────────────────────────────────────────────
"""

import asyncio
import threading

_thread = None
_loop = None
_stop_event = None
_running = False
_last_error = ""       # последняя ошибка (для диагностики из приложения)
_run_func = None       # ссылка на _run (импортируем синхронно, чтобы ошибка была видна сразу)


def last_error():
    return _last_error or ""


def is_running():
    return _running and _thread is not None and _thread.is_alive()


def start(secret_hex, fake_tls_domain, host="127.0.0.1", port=1443):
    """Поднять локальный прокси в фоновом потоке. Идемпотентно. Возвращает True при старте."""
    global _thread, _loop, _stop_event, _running, _last_error, _run_func
    if is_running():
        return True
    _last_error = ""
    # Импорт и конфиг — СИНХРОННО (в вызывающем потоке), чтобы поймать ошибки Chaquopy/стдлиба.
    try:
        from dgwsproxy.config import proxy_config
        from dgwsproxy.tg_ws_proxy import _run as run_func
        _run_func = run_func
        proxy_config.host = str(host)
        proxy_config.port = int(port)
        proxy_config.secret = str(secret_hex)
        proxy_config.fake_tls_domain = str(fake_tls_domain or "")
    except Exception as e:
        import traceback
        _last_error = "import/config: " + repr(e) + "\n" + traceback.format_exc()
        _log(_last_error)
        return False

    def run():
        global _loop, _stop_event, _running, _last_error
        try:
            _loop = asyncio.new_event_loop()
            asyncio.set_event_loop(_loop)
            _stop_event = asyncio.Event()
            _running = True
            _loop.run_until_complete(_run_func(_stop_event))
        except Exception as e:
            import traceback
            _last_error = "run: " + repr(e) + "\n" + traceback.format_exc()
            _log(_last_error)
        finally:
            _running = False
            try:
                _loop.close()
            except Exception:
                pass

    _thread = threading.Thread(target=run, name="DevGramWsProxy", daemon=True)
    _thread.start()
    return True


def stop():
    """Остановить локальный прокси."""
    global _running
    try:
        if _loop is not None and _stop_event is not None:
            _loop.call_soon_threadsafe(_stop_event.set)
    except Exception as e:
        _log("stop error: " + repr(e))
    _running = False
    return True


def _log(msg):
    try:
        from java import jclass
        jclass("org.telegram.messenger.FileLog").d("[DevGramWsProxy] " + str(msg))
    except Exception:
        pass

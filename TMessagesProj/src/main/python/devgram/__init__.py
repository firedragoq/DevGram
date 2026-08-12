"""
DevGram: Python API плагинов (аналог base_plugin у exteraGram).

Плагин — это .py-файл, который определяет класс-наследник BasePlugin.
Загрузчик (Java: DevGramPlugins) импортирует файл, находит наследника BasePlugin,
создаёт его, зовёт on_load(); хуки вызываются из Java при событиях клиента.

Минимальный плагин:

    from devgram import BasePlugin

    class MyPlugin(BasePlugin):
        id = "my_plugin"
        name = "Мой плагин"
        version = "1.0"
        author = "Vlad"

        def on_load(self):
            self.log("плагин загружен")

        def on_send_message(self, text):
            # вернуть изменённый текст, либо None — оставить как есть
            return text.replace(":love:", "❤️")
"""

from java import jclass

_FileLog = jclass("org.telegram.messenger.FileLog")
_Plugins = jclass("org.telegram.messenger.DevGramPlugins")


class BasePlugin:
    # метаданные (переопредели в своём плагине)
    id = "plugin"
    name = "Plugin"
    version = "1.0"
    author = ""
    description = ""
    icon = ""  # URL картинки-аватарки плагина (png/jpg) — показывается в списке плагинов
    # совместимость (информационно; загрузчик может предупредить при несоответствии)
    min_app_version = ""   # минимальная версия приложения (напр. "12.9")
    min_sdk = ""           # минимальная версия API плагинов DevGram (напр. "2")
    requirements = []      # список pip-зависимостей (если движок их поддерживает)

    def __init__(self):
        self.enabled = True

    # ---- жизненный цикл ----
    def on_load(self):
        """Вызывается один раз при загрузке плагина."""
        pass

    def on_unload(self):
        """Вызывается при выгрузке/отключении плагина."""
        pass

    # ---- хуки сообщений ----
    def on_send_message(self, text):
        """Перед отправкой текста. Вернуть новый текст, None (без изменений) или False (отменить отправку)."""
        return None

    def on_receive_message(self, text):
        """При получении текстового сообщения (только для чтения/логики)."""
        return None

    def on_update(self, update):
        """Сырой TL-апдейт (Java-объект TLRPC.Update). Читай поля через рефлексию. Только чтение."""
        pass

    # ---- хуки TL-запросов/ответов (client-level) ----
    def on_send_request(self, name, request):
        """Перед отправкой TL-запроса на сервер. name — имя класса запроса (напр. 'TL_messages_sendMessage'),
        request — Java-объект TLObject. Можно менять поля объекта на месте. Только для стандартного
        2-арг ConnectionsManager.sendRequest. Переопредели, чтобы включить хук."""
        return None

    def on_receive_response(self, name, response, error):
        """После ответа сервера на TL-запрос. name — имя запроса, response — Java-объект ответа (или None),
        error — текст ошибки (или None). Только чтение. Переопредели, чтобы включить хук."""
        return None

    # ---- меню сообщения ----
    def menu_items(self):
        """Названия пунктов, которые плагин добавляет в меню длинного тапа по сообщению."""
        return []

    def on_menu_click(self, label, message_text, dialog_id):
        """Клик по пункту меню, добавленному этим плагином."""
        pass

    # ---- клиентское API (client_utils) ----
    def toast(self, text):
        """Короткое всплывающее уведомление."""
        _Plugins.toast(str(text))

    def me(self):
        """ID текущего аккаунта."""
        return _Plugins.myId()

    def get_setting(self, key, default=""):
        """Прочитать сохранённую настройку плагина."""
        return _Plugins.pluginGet(self.id, str(key), str(default))

    def set_setting(self, key, value):
        """Сохранить настройку плагина (переживает перезапуск)."""
        _Plugins.pluginSet(self.id, str(key), str(value))

    def user_name(self, uid):
        """Имя пользователя по id (или '')."""
        return _Plugins.userName(int(uid))

    def chat_name(self, cid):
        """Имя чата/канала по id (или '')."""
        return _Plugins.chatName(int(cid))

    def send_message(self, dialog_id, text, reply_to=0):
        """Отправить текст в диалог. reply_to — id сообщения для ответа (0 = без ответа)."""
        if reply_to:
            _Plugins.sendMessageReply(int(dialog_id), str(text), int(reply_to))
        else:
            _Plugins.sendMessage(int(dialog_id), str(text))

    # ---- богатая отправка (client_utils) ----
    def send_photo(self, dialog_id, path, caption=""):
        """Отправить фото из файла (path — абсолютный путь)."""
        _Plugins.sendPhoto(int(dialog_id), str(path), str(caption))

    def send_file(self, dialog_id, path, caption=""):
        """Отправить файл (видео/аудио/документ — тип определяется автоматически)."""
        _Plugins.sendFile(int(dialog_id), str(path), str(caption))

    # алиасы под привычные имена
    def send_document(self, dialog_id, path, caption=""):
        self.send_file(dialog_id, path, caption)

    def send_video(self, dialog_id, path, caption=""):
        self.send_file(dialog_id, path, caption)

    def send_audio(self, dialog_id, path, caption=""):
        self.send_file(dialog_id, path, caption)

    def edit_message(self, dialog_id, message_id, text):
        """Отредактировать текст своего сообщения."""
        _Plugins.editMessageText(int(dialog_id), int(message_id), str(text))

    def send_request(self, request, callback=None):
        """Отправить сырой TL-запрос (request — Java-объект TLObject, строится через tl()).
        callback(response, error_text) зовётся при ответе. Возвращает токен запроса."""
        return _Plugins.sendRequest(request, callback)

    @staticmethod
    def tl(class_name):
        """Создать пустой TL-объект по короткому имени, напр. tl('TL_messages_getHistory').
        Возвращает Java-объект; поля заполняй как атрибуты."""
        return jclass("org.telegram.tgnet.TLRPC$" + str(class_name))()

    # ---- доступ к контроллерам (текущий аккаунт) ----
    def current_account(self):
        return _Plugins.currentAccount()

    def messages_controller(self):
        return _Plugins.messagesController()

    def connections_manager(self):
        return _Plugins.connectionsManager()

    def user_config(self):
        return _Plugins.userConfig()

    def send_messages_helper(self):
        return _Plugins.sendMessagesHelper()

    def media_data_controller(self):
        return _Plugins.mediaDataController()

    def contacts_controller(self):
        return _Plugins.contactsController()

    def messages_storage(self):
        return _Plugins.messagesStorage()

    def notification_center(self):
        return _Plugins.notificationCenter()

    def file_loader(self):
        return _Plugins.fileLoader()

    def account_instance(self):
        return _Plugins.accountInstance()

    # ---- потоки (android_utils) ----
    def run_on_ui(self, fn, delay=0):
        """Выполнить функцию на UI-потоке (delay — задержка в мс)."""
        _Plugins.runOnUi(fn, int(delay))

    def run_on_queue(self, fn):
        """Выполнить функцию в фоновой очереди (не блокирует UI)."""
        _Plugins.runOnQueue(fn)

    # ---- реализация Java-интерфейсов из Python (class-proxy для интерфейсов) ----
    @staticmethod
    def implement(interface, **methods):
        """Создать Java-объект, реализующий интерфейс(ы), методами на Python.
        interface — Java-класс (jclass) или кортеж классов; methods — {имя_метода: функция(self, *args)}.
        Пример: l = self.implement(jclass('android.view.View$OnClickListener'), onClick=lambda s,v: ...).
        Покрывает слушатели/делегаты (OnClickListener, RequestDelegate, NotificationCenterDelegate)."""
        from java import dynamic_proxy
        ifaces = interface if isinstance(interface, tuple) else (interface,)
        proxy_cls = type("DevGramProxy", (dynamic_proxy(*ifaces),), dict(methods))
        return proxy_cls()

    def on_click(self, fn):
        """Java View.OnClickListener из Python-функции fn(view)."""
        OCL = jclass("android.view.View$OnClickListener")
        return self.implement(OCL, onClick=lambda s, v: fn(v))

    def on_long_click(self, fn):
        """Java View.OnLongClickListener из Python-функции fn(view)->bool."""
        OLCL = jclass("android.view.View$OnLongClickListener")
        return self.implement(OLCL, onLongClick=lambda s, v: bool(fn(v)))

    # ---- настоящий Java-подкласс из Python (Class Proxy на DexMaker) ----
    @staticmethod
    def _alist(items):
        """Python-список -> java.util.ArrayList (Chaquopy не конвертит list->List сам)."""
        al = jclass("java.util.ArrayList")()
        if items:
            for x in items:
                al.add(x)
        return al

    @staticmethod
    def _override_names(logic):
        """Имена методов, определённых в классе logic (для маршрутизации в override)."""
        names = set()
        for cls in type(logic).__mro__:
            if cls is object:
                break
            for k, v in cls.__dict__.items():
                if callable(v) and not k.startswith("__"):
                    names.add(k)
        return names

    def java_class(self, base_class_name, logic, arg_types=None, args=None):
        """Создать РЕАЛЬНЫЙ Java-подкласс base_class_name (напр. 'android.view.View').
        logic — Python-объект: его одноимённые методы переопределяют Java-методы; сигнатура
        метода — (self, this, *java_args), где this — сам Java-объект (для call_super).
        arg_types/args — типы и значения аргументов конструктора базового класса
        (напр. arg_types=['android.content.Context'], args=[context]).
        Возвращает Java-инстанс подкласса (или None). Пример:

            class Logic:
                def onDraw(self, this, canvas):
                    BasePlugin.java_super()          # super.onDraw(canvas)
                    canvas.drawColor(0x2200FF00)
            view = self.java_class('android.view.View', Logic(),
                                   arg_types=['android.content.Context'], args=[ctx])
        """
        try:
            names = self._override_names(logic)
            return _Plugins.subclass(
                str(base_class_name), logic,
                self._alist(list(names)),
                self._alist(arg_types),
                self._alist(args),
            )
        except Exception as e:
            self.log("java_class: " + str(e))
            return None

    @staticmethod
    def java_super(*args):
        """Вызвать оригинальный (super) метод из тела Python-override.
        Без аргументов — те же аргументы, что пришли; с аргументами — переопределить их.
        Возвращает результат super-метода."""
        if args:
            al = jclass("java.util.ArrayList")()
            for x in args:
                al.add(x)
            return _Plugins.callSuper(al)
        return _Plugins.callSuper(None)

    # ---- android_utils ----
    def copy(self, text):
        """Скопировать текст в буфер обмена."""
        _Plugins.copyToClipboard(str(text))

    def clipboard(self):
        """Прочитать текст из буфера обмена."""
        return _Plugins.getClipboard()

    # ---- file_utils (файлы в личной папке плагина) ----
    def read_file(self, name):
        """Прочитать файл из личной папки плагина (или '')."""
        return _Plugins.readData(self.id, str(name))

    def write_file(self, name, content):
        """Записать файл в личную папку плагина."""
        _Plugins.writeData(self.id, str(name), str(content))

    # ---- диалоги / bulletins ----
    def bulletin(self, text):
        """Показать плашку-bulletin (в стиле Telegram)."""
        _Plugins.bulletin(str(text))

    def alert(self, title, message=""):
        """Показать диалог с заголовком и текстом."""
        _Plugins.alert(str(title), str(message))

    # ---- UI: страница настроек плагина ----
    def settings(self):
        """Строки настроек экрана плагина. Каждая — кортеж:
        ("header", "", "Заголовок")               — раздел
        ("switch", "ключ", "Название")            — переключатель (хранится 1/0)
        ("text",   "ключ", "Название")            — текстовое поле
        ("button", "ключ", "Название")            — кнопка (зовёт on_setting_click)
        Значения читаются/пишутся через get_setting/set_setting."""
        return []

    def on_setting_click(self, key):
        """Клик по кнопке-настройке (type='button')."""
        pass

    # ---- Xposed-хуки Java-методов (hook_utils) ----
    def hook(self, class_name, method_name, *param_types):
        """Хукнуть Java-метод/конструктор. Реализуй before_hook / after_hook.
        param_types — типы аргументов: 'int','long','boolean','java.lang.String' и т.п.
        method_name='<init>' — конструктор. Возвращает True при успехе."""
        # Chaquopy не конвертирует python-list в java.util.List — собираем ArrayList явно
        pts = jclass("java.util.ArrayList")()
        for t in param_types:
            pts.add(str(t))
        return _Plugins.hook(self.id, str(class_name), str(method_name), pts)

    def before_hook(self, frame):
        """До оригинала. frame.args — аргументы (можно менять: frame.args[0]=...),
        frame.thisObject — объект, frame.setResult(x) — заменить результат и ПРОПУСТИТЬ оригинал,
        frame.method — какой метод сработал."""
        pass

    def after_hook(self, frame):
        """После оригинала. frame.getResult() / frame.setResult(x) — прочитать/подменить результат."""
        pass

    # ---- визуальные эффекты (ui_effects) ----
    @staticmethod
    def _i32(color):
        """ARGB -> знаковый java int (0xFF.. не влезает в signed int)."""
        color = int(color)
        return color - 0x100000000 if color >= 0x80000000 else color

    @staticmethod
    def _api():
        try:
            return jclass("android.os.Build$VERSION").SDK_INT
        except Exception:
            return 0

    # --- Жидкое стекло (Liquid Glass) — настоящий backdrop-blur, работает на всех Android ---

    def glass(self, view, corner=22, blur=18, tint=0x26FFFFFF, border=0.6):
        """Обернуть СУЩЕСТВУЮЩУЮ вью в жидкое стекло (размывается фон ПОЗАДИ неё).
        view вынимается из родителя и кладётся внутрь стеклянной панели на то же место.
        corner — скругление (dp), blur — сила размытия (dp), tint — тонировка ARGB
        (напр. 0x26FFFFFF), border — яркость светящейся кромки 0..1.
        Возвращает стеклянную панель (в неё же можно добавлять контент) или None.
        Обычно: g = self.glass(frame.thisObject)  # напр. поле ввода / шапку чата."""
        try:
            return _Plugins.glassWrap(view, int(corner), int(blur), self._i32(tint), float(border))
        except Exception as e:
            self.log("glass: " + str(e))
            return None

    def glass_panel(self, context_view=None, corner=22, blur=18, tint=0x26FFFFFF, border=0.6):
        """Создать НОВУЮ пустую стеклянную панель-контейнер (FrameLayout). Добавь её куда нужно
        через add_view(...) и клади внутрь свой контент. context_view — любая вью для контекста
        и корня размытия (обычно frame.thisObject). Возвращает панель или None."""
        try:
            return _Plugins.glassPanel(context_view, int(corner), int(blur), self._i32(tint), float(border))
        except Exception as e:
            self.log("glass_panel: " + str(e))
            return None

    def add_view(self, parent, child, width=-1, height=-1, left=0, top=0, right=0, bottom=0, gravity=0):
        """Добавить child во ViewGroup parent. width/height в dp (или -1=на весь размер, -2=по контенту),
        отступы left/top/right/bottom в dp, gravity как android Gravity (17 = центр, 80 = снизу, 48 = сверху)."""
        try:
            _Plugins.addViewFrame(parent, child, int(width), int(height),
                                  int(left), int(top), int(right), int(bottom), int(gravity))
            return True
        except Exception as e:
            self.log("add_view: " + str(e))
            return False

    def remove_view(self, view):
        """Убрать вью из её родителя."""
        try:
            _Plugins.removeView(view)
            return True
        except Exception:
            return False

    def dp(self, value):
        """Перевести dp в пиксели (для расчётов размеров)."""
        return _Plugins.dp(float(value))

    @staticmethod
    def rgba(r, g, b, a=255):
        """Собрать ARGB-цвет из компонент 0..255 (удобно для tint)."""
        return ((int(a) & 0xFF) << 24) | ((int(r) & 0xFF) << 16) | ((int(g) & 0xFF) << 8) | (int(b) & 0xFF)

    def blur(self, view, radius=40.0):
        """Заморозить СОБСТВЕННЫЙ контент View (не фон!). Android 12+ (API 31). True при успехе.
        Для стеклянной панели с размытием ФОНА используй glass()/glass_panel().
        Обычно зовётся из before_hook/after_hook, где view = frame.thisObject."""
        try:
            if self._api() < 31 or view is None:
                return False
            RE = jclass("android.graphics.RenderEffect")
            Shader = jclass("android.graphics.Shader")
            view.setRenderEffect(RE.createBlurEffect(float(radius), float(radius), Shader.TileMode.CLAMP))
            return True
        except Exception as e:
            self.log("blur: " + str(e))
            return False

    def unblur(self, view):
        """Снять размытие с View."""
        try:
            view.setRenderEffect(None)
            return True
        except Exception:
            return False

    def tint(self, view, argb):
        """Полупрозрачная заливка фона View (ARGB, напр. 0x55101018 — стеклянная тонировка)."""
        try:
            if view is None:
                return False
            view.setBackgroundColor(self._i32(argb))
            return True
        except Exception as e:
            self.log("tint: " + str(e))
            return False

    def round_corners(self, view, radius_dp=18):
        """Скруглить углы View (обрезка по контуру)."""
        try:
            if view is None:
                return False
            _Plugins.roundView(view, int(radius_dp))
            return True
        except Exception as e:
            self.log("round: " + str(e))
            return False

    # ---- утилиты ----
    def log(self, msg):
        _FileLog.d("[DevGramPlugin:%s] %s" % (self.id, msg))

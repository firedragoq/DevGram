"""
DevGram: загрузчик и реестр плагинов. Вызывается из Java (DevGramPlugins).
Импортирует .py-файлы, находит наследников devgram.BasePlugin, хранит их,
и раздаёт события (хуки) по включённым плагинам.
"""

import ast
import importlib.util
import os
import sys
import traceback

from java import jclass

_FileLog = jclass("org.telegram.messenger.FileLog")
_SEP = "\x1f"  # разделитель полей для передачи в Java

_plugins = []  # экземпляры плагинов


def _log(msg):
    _FileLog.d("[DevGramPlugins] " + str(msg))


def hello():
    return "DevGram Python OK, version " + sys.version.split()[0]


def python_version():
    return sys.version.split()[0]


def unload_plugin(plugin_id):
    _unload_id(plugin_id)
    return True


def reload_all(dir_path):
    """Выгрузить все плагины и загрузить папку заново (без перезапуска приложения)."""
    for p in list(_plugins):
        try:
            p.on_unload()
        except Exception:
            pass
    _plugins.clear()
    return load_dir(dir_path)


def parse_meta(source):
    """БЕЗ выполнения кода (ast) извлечь метаданные из исходника .plugin.
    Возвращает 'id␟name␟version␟author␟description␟icon' или '' если это не плагин DevGram."""
    try:
        tree = ast.parse(source)
    except Exception:
        return ""
    meta = {"id": "", "name": "", "version": "", "author": "", "description": "", "icon": ""}
    for node in ast.walk(tree):
        if isinstance(node, ast.ClassDef):
            for item in node.body:
                if isinstance(item, ast.Assign):
                    for t in item.targets:
                        if isinstance(t, ast.Name) and t.id in meta:
                            v = item.value
                            if isinstance(v, ast.Constant) and isinstance(v.value, str):
                                meta[t.id] = v.value
    if not meta["name"] and not meta["id"]:
        return ""
    return _SEP.join([meta["id"], meta["name"], meta["version"], meta["author"], meta["description"], meta["icon"]])


def _unload_id(plugin_id):
    """Выгрузить уже загруженный плагин с таким id (для переустановки)."""
    for p in list(_plugins):
        if str(p.id) == str(plugin_id):
            try:
                p.on_unload()
            except Exception:
                pass
            _plugins.remove(p)


def load_from_file(path):
    """Загрузить один .py/.plugin-файл. Возвращает число найденных плагинов."""
    from devgram import BasePlugin
    found = 0
    try:
        mod_name = "devgram_plugin_" + os.path.splitext(os.path.basename(path))[0]
        spec = importlib.util.spec_from_file_location(mod_name, path)
        module = importlib.util.module_from_spec(spec)
        spec.loader.exec_module(module)
        for attr in dir(module):
            obj = getattr(module, attr)
            if isinstance(obj, type) and issubclass(obj, BasePlugin) and obj is not BasePlugin:
                inst = obj()
                inst._path = path
                inst._filename = os.path.basename(path)
                _unload_id(inst.id)  # если уже загружен такой id — заменяем (переустановка)
                _plugins.append(inst)
                try:
                    inst.on_load()
                except Exception:
                    _log("on_load error: " + traceback.format_exc())
                found += 1
                _log("loaded plugin: %s (%s)" % (inst.name, inst.id))
    except Exception:
        _log("load error %s: %s" % (path, traceback.format_exc()))
    return found


def load_dir(dir_path):
    """Загрузить все .py из папки. Возвращает число загруженных плагинов."""
    if not dir_path or not os.path.isdir(dir_path):
        return 0
    n = 0
    for f in sorted(os.listdir(dir_path)):
        if f.endswith(".py") or f.endswith(".plugin"):
            n += load_from_file(os.path.join(dir_path, f))
    return n


CANCEL = "\x00DEVGRAM_CANCEL\x00"  # маркер отмены отправки (сверяется в Java)


def dispatch_send(text):
    """Хук исходящего текста: плагины меняют текст (return str), отменяют (return False) или ничего (None)."""
    for p in _plugins:
        if not getattr(p, "enabled", True):
            continue
        try:
            r = p.on_send_message(text)
            if r is False:
                return CANCEL
            if r is not None:
                text = r
        except Exception:
            _log("on_send_message error: " + traceback.format_exc())
    return text


def dispatch_receive(text):
    """Хук входящего текста (только чтение/логика)."""
    for p in _plugins:
        if not getattr(p, "enabled", True):
            continue
        try:
            p.on_receive_message(text)
        except Exception:
            _log("on_receive_message error: " + traceback.format_exc())


def dispatch_hook(plugin_id, phase, frame):
    """Мост Pine-хука в плагин: phase = 'before'/'after'."""
    for p in _plugins:
        if str(p.id) == str(plugin_id):
            if not getattr(p, "enabled", True):
                return
            try:
                if phase == "before":
                    p.before_hook(frame)
                else:
                    p.after_hook(frame)
            except Exception:
                _log("hook dispatch error: " + traceback.format_exc())
            return


def wants_updates():
    """True, если хоть один включённый плагин переопределил on_update (чтобы не грузить зря)."""
    from devgram import BasePlugin
    for p in _plugins:
        if not getattr(p, "enabled", True):
            continue
        if type(p).on_update is not BasePlugin.on_update:
            return True
    return False


def dispatch_update(update):
    """Хук сырых TL-апдейтов."""
    for p in _plugins:
        if not getattr(p, "enabled", True):
            continue
        try:
            p.on_update(update)
        except Exception:
            _log("on_update error: " + traceback.format_exc())


def menu_items():
    """Пункты меню сообщения от всех включённых плагинов: строки 'pluginId␟label'."""
    res = []
    for p in _plugins:
        if not getattr(p, "enabled", True):
            continue
        try:
            for label in (p.menu_items() or []):
                res.append(_SEP.join([str(p.id), str(label)]))
        except Exception:
            _log("menu_items error: " + traceback.format_exc())
    return res


def menu_click(plugin_id, label, message_text, dialog_id):
    for p in _plugins:
        if str(p.id) == str(plugin_id):
            try:
                p.on_menu_click(label, message_text, dialog_id)
            except Exception:
                _log("on_menu_click error: " + traceback.format_exc())
            return


def plugin_settings(plugin_id):
    """Строки настроек плагина: 'type␟key␟title'."""
    for p in _plugins:
        if str(p.id) == str(plugin_id):
            try:
                res = []
                for r in (p.settings() or []):
                    t = str(r[0])
                    k = str(r[1]) if len(r) > 1 else ""
                    title = str(r[2]) if len(r) > 2 else ""
                    res.append(_SEP.join([t, k, title]))
                return res
            except Exception:
                _log("settings error: " + traceback.format_exc())
            return []
    return []


def plugin_setting_click(plugin_id, key):
    for p in _plugins:
        if str(p.id) == str(plugin_id):
            try:
                p.on_setting_click(key)
            except Exception:
                _log("on_setting_click error: " + traceback.format_exc())
            return


def list_plugins():
    """Для менеджера: 'id␟name␟version␟author␟enabled␟filename␟description␟icon'."""
    res = []
    for p in _plugins:
        en = 1 if getattr(p, "enabled", True) else 0
        res.append(_SEP.join([
            str(p.id), str(p.name), str(p.version), str(p.author),
            str(en), str(getattr(p, "_filename", "")), str(getattr(p, "description", "")),
            str(getattr(p, "icon", "")),
        ]))
    return res


def set_enabled(plugin_id, enabled):
    for p in _plugins:
        if str(p.id) == str(plugin_id):
            p.enabled = bool(enabled)
            return True
    return False


def count():
    return len(_plugins)

from datetime import datetime

from devgram import BasePlugin
from devgram.ui import Button, Header, Switch, Text


class DevServerDemo(BasePlugin):
    id = "space.devgram.devserverdemo"
    name = "Dev Server Demo"
    version = "1.0.0"
    author = "FireDragoq"
    description = "Проверка Dev Server, hot reload и нативного Plugin SDK"
    icon = "https://devgram.space/favicon.png"

    def on_load(self):
        self.loaded_at = datetime.now().strftime("%H:%M:%S")
        try:
            launches = int(self.get_setting("launches", "0")) + 1
        except (TypeError, ValueError):
            launches = 1
        self.set_setting("launches", str(launches))
        self.log("Dev Server Demo loaded at " + self.loaded_at)
        if self.get_setting("show_on_load", "1") in ("1", "true", "True"):
            self.bulletin(
                "Dev Server Demo загружен • " + self.loaded_at,
                kind="success",
            )

    def settings(self):
        launches = self.get_setting("launches", "0")
        return [
            Header(text="Dev Server Demo"),
            Text(text="Загрузок через runtime: " + launches),
            Switch(
                key="show_on_load",
                text="Показывать плашку при hot reload",
                default=True,
            ),
            Button(key="ping", text="Проверить плагин"),
            Button(key="copy_status", text="Скопировать статус"),
            Button(key="reset_counter", text="Сбросить счётчик загрузок"),
        ]

    def on_setting_click(self, key):
        if key == "ping":
            self.bulletin(
                "Плагин работает • загружен в " + self.loaded_at,
                kind="success",
            )
        elif key == "copy_status":
            status = (
                "Dev Server Demo\n"
                "Version: " + self.version + "\n"
                "Loaded at: " + self.loaded_at + "\n"
                "Runtime loads: " + self.get_setting("launches", "0")
            )
            self.copy(status)
            self.bulletin("Статус скопирован", kind="info")
        elif key == "reset_counter":
            self.set_setting("launches", "0")
            self.bulletin("Счётчик сброшен", kind="success")

    def menu_items(self):
        return ["⚡ Dev Server Demo: копировать текст"]

    def on_menu_click(self, label, message_text, dialog_id):
        if label.startswith("⚡ Dev Server Demo"):
            self.copy(message_text or "")
            self.bulletin("Текст сообщения скопирован", kind="success")


plugin = DevServerDemo()

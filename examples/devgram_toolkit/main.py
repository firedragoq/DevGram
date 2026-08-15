from devgram import BasePlugin
from devgram.ui import Header, Switch, Selector, Button, Text

from modules.message_tools import MessageTools
from services.counter import CounterService


class DevGramToolkit(BasePlugin):
    id = "space.devgram.toolkit"
    name = "DevGram Toolkit"
    version = "1.0.0"
    author = "firedragoq"
    description = "Builder-style demo plugin for DevGram"
    min_app_version = "12.9.3"

    def on_load(self):
        self.counter = CounterService(self)
        self.messages = MessageTools(self, self.counter)
        self._active = True

    def on_unload(self):
        self._active = False
        if hasattr(self, "messages"):
            self.messages.close()

    def settings(self):
        return [
            Header(text="DevGram Toolkit"),
            Text(text="Демонстрация модульной структуры Builder"),
            Switch(
                key="enabled_tools",
                text="Показывать инструменты сообщений",
                default=True,
            ),
            Selector(
                key="text_mode",
                text="Преобразование текста",
                items=["ВЕРХНИЙ РЕГИСТР", "нижний регистр", "Title Case"],
            ),
            Button(key="show_stats", text="Показать статистику"),
            Button(key="reset_stats", text="Сбросить статистику"),
        ]

    def on_setting_click(self, key):
        if key == "show_stats":
            self.bulletin(
                "Обработано действий: " + str(self.counter.value()),
                kind="success",
            )
        elif key == "reset_stats":
            self.counter.reset()
            self.bulletin("Статистика сброшена", kind="success")

    def menu_items(self):
        if self.get_setting("enabled_tools", "1") not in ("1", "true", "True"):
            return []
        return self.messages.items()

    def on_menu_click(self, label, message_text, dialog_id):
        self.messages.handle(label, message_text or "", dialog_id)


plugin = DevGramToolkit()

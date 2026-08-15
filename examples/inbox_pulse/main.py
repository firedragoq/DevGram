from java import jclass

from devgram import BasePlugin
from devgram.intents import open_uri
from devgram.ui import Button, Header, Selector, Switch, Text


class InboxPulse(BasePlugin):
    id = "space.devgram.inboxpulse"
    name = "Inbox Pulse"
    version = "1.0.1"
    author = "FireDragoq"
    description = "Красивый Pill Stack-виджет непрочитанных сообщений с быстрым переходом к следующему чату"
    icon = "https://devgram.space/favicon.png"

    def on_load(self):
        self._live_pills = []
        self._proxy_refs = []
        self._last_dialog_id = 0
        self._install_pill()

    def on_unload(self):
        self.unregister_pills()
        self._live_pills = []
        self._proxy_refs = []

    def _install_pill(self):
        # Public registration keeps ownership, active/hidden state and cleanup correct.
        self._pill_id = self.register_pill(
            "inbox",
            "Inbox Pulse",
            text=self._pill_text(),
            value=self._pill_text,
            on_click=self._open_next_unread,
            on_long_click=self._toggle_mode,
            enabled=True,
        )

        # Replace only the native factory for this owned ID to add a polished gradient.
        try:
            config = jclass("org.telegram.ui.pillstack.PillStackConfig")
            pill_info = jclass("org.telegram.ui.pillstack.PillStackConfig$PillInfo")
            creator_type = jclass("org.telegram.ui.pillstack.PillStackConfig$PillCreator")
            drawables = jclass("org.telegram.messenger.R$drawable")
            self._creator = self.implement(
                creator_type,
                create=lambda proxy, context, provider: self._create_native_pill(context, provider),
            )
            self._proxy_refs.append(self._creator)
            self._config = config
            self._pill_info = pill_info(
                int(self._pill_id),
                "Inbox Pulse",
                drawables.msg_markunread,
                self._signed_color(0xFF8B5CF6),
                self._signed_color(0xFF3B82F6),
                self._creator,
            )
            config.register(self._pill_info)
        except Exception as error:
            # The normal public pill registered above remains fully functional.
            self.log("Inbox Pulse gradient fallback: " + repr(error))

    def _create_native_pill(self, context, provider):
        plugin_pill = jclass("org.telegram.ui.pillstack.pills.PluginPill")
        value_type = jclass("org.telegram.ui.pillstack.pills.PluginPill$ValueProvider")
        runnable = jclass("java.lang.Runnable")
        holder = {}

        value_proxy = self.implement(value_type, get=lambda proxy: self._pill_text())
        click_proxy = self.implement(
            runnable,
            run=lambda proxy: self._open_next_unread(holder.get("pill")),
        )
        long_click_proxy = self.implement(
            runnable,
            run=lambda proxy: self._toggle_mode(holder.get("pill")),
        )
        self._proxy_refs.extend((value_proxy, click_proxy, long_click_proxy))

        pill = plugin_pill(
            context,
            provider,
            int(self._pill_id),
            self._pill_text(),
            value_proxy,
            click_proxy,
            long_click_proxy,
        )
        holder["pill"] = pill
        self._live_pills.append(pill)
        self._apply_style(pill)
        return pill

    @staticmethod
    def _signed_color(color):
        return color - 0x100000000 if color > 0x7FFFFFFF else color

    def _controller(self):
        account = int(jclass("org.telegram.messenger.UserConfig").selectedAccount)
        return jclass("org.telegram.messenger.MessagesController").getInstance(account)

    def _unread_dialogs(self):
        result = []
        try:
            controller = self._controller()
            dialog_object = jclass("org.telegram.messenger.DialogObject")
            hide_muted = self.get_setting("hide_muted", "0") == "1"
            dialogs = controller.getAllDialogs()
            for index in range(int(dialogs.size())):
                dialog = dialogs.get(index)
                dialog_id = int(dialog.id)
                if not dialog_id or dialog_object.isFolderDialogId(dialog_id):
                    continue
                unread = max(0, int(dialog.unread_count))
                marked = bool(dialog.unread_mark)
                if unread == 0 and not marked:
                    continue
                if hide_muted and controller.isDialogMuted(dialog_id, 0):
                    continue
                result.append((dialog_id, unread))
        except Exception as error:
            self.log("Inbox Pulse read error: " + repr(error))
        return result

    def _stats(self):
        dialogs = self._unread_dialogs()
        # A manually marked-unread dialog has unread_count == 0 but still represents
        # one unread item from the user's point of view.
        return dialogs, len(dialogs), sum(max(1, row[1]) for row in dialogs)

    @staticmethod
    def _plural(value, one, few, many):
        value = abs(int(value))
        if value % 10 == 1 and value % 100 != 11:
            return one
        if value % 10 in (2, 3, 4) and value % 100 not in (12, 13, 14):
            return few
        return many

    def _pill_text(self):
        dialogs, chat_count, message_count = self._stats()
        if not dialogs:
            return "✓  Всё прочитано"
        if self.get_setting("count_mode", "0") == "1":
            word = self._plural(message_count, "сообщение", "сообщения", "сообщений")
            return "✦  %d %s" % (message_count, word)
        word = self._plural(chat_count, "чат", "чата", "чатов")
        return "✦  %d %s" % (chat_count, word)

    def _apply_style(self, pill):
        if pill is None:
            return
        try:
            has_unread = bool(self._unread_dialogs())
            if has_unread:
                top, bottom = 0xFF8B5CF6, 0xFF3B82F6
            else:
                top, bottom = 0xFF34D399, 0xFF0891B2
            layout = pill.getChildAt(0)
            background = jclass("org.telegram.ui.pillstack.pills.ColoredBackground")(
                self._signed_color(top), self._signed_color(bottom)
            )
            layout.setBackground(background)
            layout.setElevation(float(jclass("org.telegram.messenger.AndroidUtilities").dp(2)))
            text_view = layout.getChildAt(0)
            text_view.setTextColor(-1)
            pill.setContentDescription(self._pill_text())
        except Exception as error:
            self.log("Inbox Pulse style error: " + repr(error))

    def _refresh(self, preferred=None):
        pills = list(self._live_pills)
        if preferred is not None and preferred not in pills:
            pills.append(preferred)
        for pill in pills:
            try:
                pill.onUpdateData(True)
                self._apply_style(pill)
            except Exception:
                pass

    def _rebuild(self):
        """Recreate the native pill so shrinking text is applied on older DevGram builds."""
        try:
            self._live_pills = []
            self._config.register(self._pill_info)
        except Exception:
            self._refresh()

    def _open_next_unread(self, pill=None):
        dialogs = self._unread_dialogs()
        if not dialogs:
            self.bulletin("Все чаты прочитаны", kind="success")
            self._refresh(pill)
            return

        ids = [row[0] for row in dialogs]
        try:
            position = ids.index(self._last_dialog_id) + 1
        except ValueError:
            position = 0
        dialog_id = ids[position % len(ids)]
        self._last_dialog_id = dialog_id

        try:
            dialog_object = jclass("org.telegram.messenger.DialogObject")
            if dialog_object.isEncryptedDialog(dialog_id):
                self.bulletin("Секретный чат пропущен", kind="info")
                return
            if dialog_object.isUserDialog(dialog_id):
                uri = "tg://openmessage?user_id=%d" % dialog_id
            elif dialog_object.isChatDialog(dialog_id):
                uri = "tg://openmessage?chat_id=%d" % (-dialog_id)
            else:
                self.bulletin("Этот диалог нельзя открыть из виджета", kind="error")
                return
            if not open_uri(uri):
                raise RuntimeError("Telegram URI was not handled")
        except Exception as error:
            self.log("Inbox Pulse navigation error: " + repr(error))
            self.bulletin("Не удалось открыть непрочитанный чат", kind="error")
        finally:
            self._refresh(pill)

    def _toggle_mode(self, pill=None):
        next_mode = "0" if self.get_setting("count_mode", "0") == "1" else "1"
        self.set_setting("count_mode", next_mode)
        self._rebuild()
        self.bulletin(
            "Теперь считаются " + ("сообщения" if next_mode == "1" else "чаты"),
            kind="info",
        )

    def settings(self):
        dialogs, chat_count, message_count = self._stats()
        return [
            Header(text="Inbox Pulse"),
            Text(text="Сейчас: %d чатов • %d сообщений" % (chat_count, message_count)),
            Selector(
                key="count_mode",
                text="Счётчик в виджете",
                items=["Непрочитанные чаты", "Непрочитанные сообщения"],
            ),
            Switch(key="hide_muted", text="Не учитывать заглушённые чаты", default=False),
            Button(key="open_next", text="Открыть следующий непрочитанный чат"),
            Button(key="refresh", text="Обновить счётчик"),
        ]

    def on_setting_changed(self, key, value):
        if key in ("count_mode", "hide_muted"):
            self._rebuild()

    def on_setting_click(self, key):
        if key == "open_next":
            self._open_next_unread()
        elif key == "refresh":
            self._refresh()
            self.bulletin("Счётчик обновлён", kind="success")


plugin = InboxPulse()

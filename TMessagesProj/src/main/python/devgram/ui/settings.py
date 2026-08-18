"""Declarative settings controls supported by DevGram's native settings screen."""
class Setting:
    kind = "button"
    def __init__(self, key="", text="", default=None, on_change=None, items=None, **kwargs):
        self.key, self.text, self.default, self.on_change, self.items = key, text, default, on_change, items or []
    def as_row(self): return self.kind, self.key, self.text, "|".join(str(item) for item in self.items)

class Header(Setting):
    kind = "header"
    def __init__(self, text="", **kwargs): super().__init__(text=text)

class Switch(Setting): kind = "switch"
class Input(Setting): kind = "text"
class Selector(Setting): kind = "selector"
class Text(Setting): kind = "info"
class Button(Setting): kind = "button"

class Card(Setting):
    """Цветная карточка-баннер (иконка + заголовок + подзаголовок), кликабельная —
    зовёт on_setting_click(key), как Button. icon — один символ/эмодзи, color — ARGB int
    (напр. 0xFFE0234A). Пример: Card(key='connect', text='Платформы',
    subtitle='Настроить подключение', icon='🌐', color=0xFFE0234A)."""
    kind = "card"
    def __init__(self, key="", text="", subtitle="", icon="", color=0xFF3B82F6, **kwargs):
        super().__init__(key=key, text=text)
        self.subtitle, self.icon, self.color = subtitle, icon, int(color)
    def as_row(self):
        # Java int — знаковый 32-битный: ARGB с альфой (>=0x80000000) должен уйти как
        # отрицательное число, иначе Integer.parseInt на приёмной стороне упадёт.
        c = self.color & 0xFFFFFFFF
        signed = c - 0x100000000 if c >= 0x80000000 else c
        extra = "\x01".join([self.icon or "", self.subtitle or "", str(signed)])
        return self.kind, self.key, self.text, extra

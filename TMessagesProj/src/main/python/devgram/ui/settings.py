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

class Custom(Setting):
    """Полностью свой Android View вместо любой из встроенных строк (Header/Switch/Card/...).
    Разработчик сам строит вью через java_class()/java (см. BasePlugin.java_class,
    from java import jclass) — размер, стиль, поведение по тапу — целиком его. DevGram
    просто встраивает готовый View в список настроек, ничего не навязывает.

    view — уже построенный Java-объект View (не строка!). height_dp=0 — строка сама
    подстраивается под контент (WRAP_CONTENT); задайте число, если нужна точная высота.

    Пример:
        from java import jclass
        Button = jclass('android.widget.Button')
        btn = Button(context)
        btn.setText('Моя кнопка')
        Custom(view=btn)
    """
    kind = "custom_view"
    def __init__(self, view=None, height_dp=0, **kwargs):
        super().__init__()
        self.view, self.height_dp = view, height_dp
    def as_row(self):
        return self.kind, "", "", str(int(self.height_dp))

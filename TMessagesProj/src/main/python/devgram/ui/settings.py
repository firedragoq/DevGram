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

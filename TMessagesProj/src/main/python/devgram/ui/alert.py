from devgram import _Plugins

class AlertDialogBuilder:
    def __init__(self, context=None, progress_style=0, resources_provider=None):
        self.title = ""; self.message = ""
        self.positive = ("OK", None)
        self.negative = (None, None)
        self.neutral = (None, None)
    def set_title(self, value): self.title = str(value); return self
    def set_message(self, value): self.message = str(value); return self
    def set_positive_button(self, text, listener=None):
        self.positive = (None if text is None else str(text), listener); return self
    def set_negative_button(self, text, listener=None):
        self.negative = (None if text is None else str(text), listener); return self
    def set_neutral_button(self, text, listener=None):
        self.neutral = (None if text is None else str(text), listener); return self
    def create(self): return self
    def show(self):
        _Plugins.alert(self.title, self.message,
                       self.positive[0], self.positive[1],
                       self.negative[0], self.negative[1],
                       self.neutral[0], self.neutral[1])
        return self
    def dismiss(self): return self

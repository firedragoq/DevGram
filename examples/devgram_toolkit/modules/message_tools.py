class MessageTools:
    COPY = "Toolkit: копировать текст"
    TRANSFORM = "Toolkit: преобразовать текст"
    INFO = "Toolkit: информация о сообщении"

    def __init__(self, plugin, counter):
        self.plugin = plugin
        self.counter = counter
        self.closed = False

    def items(self):
        return [self.COPY, self.TRANSFORM, self.INFO]

    def handle(self, label, text, dialog_id):
        if self.closed:
            return
        if label == self.COPY:
            self.plugin.copy(text)
            self.counter.increment()
            self.plugin.bulletin("Текст скопирован", kind="success")
            return
        if label == self.TRANSFORM:
            mode = self.plugin.get_setting("text_mode", "0")
            transformed = self._transform(text, mode)
            self.plugin.copy(transformed)
            self.counter.increment()
            self.plugin.bulletin("Преобразованный текст скопирован", kind="success")
            return
        if label == self.INFO:
            words = len(text.split())
            chars = len(text)
            self.counter.increment()
            self.plugin.alert(
                "Информация о сообщении",
                "Символов: %d\nСлов: %d\nDialog ID: %s" % (chars, words, dialog_id),
            )

    @staticmethod
    def _transform(text, mode):
        if str(mode) in ("1", "нижний регистр"):
            return text.lower()
        if str(mode) in ("2", "Title Case"):
            return text.title()
        return text.upper()

    def close(self):
        self.closed = True

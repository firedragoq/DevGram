from devgram import _Plugins

class BulletinHelper:
    DURATION_SHORT = 1500
    DURATION_LONG = 2750
    DURATION_PROLONG = 5000

    @staticmethod
    def _show(kind, message, duration, button, callback):
        _Plugins.bulletin(kind, str(message), int(duration),
                          None if button is None else str(button), callback)

    @classmethod
    def show_info(cls, message, fragment=None, duration=DURATION_LONG,
                  button=None, callback=None):
        cls._show("info", message, duration, button, callback)

    @classmethod
    def show_success(cls, message, fragment=None, duration=DURATION_SHORT,
                     button=None, callback=None):
        cls._show("success", message, duration, button, callback)

    @classmethod
    def show_error(cls, message, fragment=None, duration=DURATION_LONG,
                   button=None, callback=None):
        cls._show("error", message, duration, button, callback)

    @classmethod
    def show(cls, message, kind="info", duration=DURATION_LONG,
             button=None, callback=None, fragment=None):
        cls._show(str(kind), message, duration, button, callback)

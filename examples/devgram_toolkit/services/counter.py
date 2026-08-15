class CounterService:
    KEY = "actions_count"

    def __init__(self, plugin):
        self.plugin = plugin

    def value(self):
        try:
            return int(self.plugin.get_setting(self.KEY, "0"))
        except (TypeError, ValueError):
            return 0

    def increment(self):
        value = self.value() + 1
        self.plugin.set_setting(self.KEY, str(value))
        return value

    def reset(self):
        self.plugin.set_setting(self.KEY, "0")

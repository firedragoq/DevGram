"""Android callback and thread helpers."""
from devgram import _Plugins
from java import dynamic_proxy, jclass

def run_on_ui_thread(func, delay=0): _Plugins.runOnUi(func, int(delay))
def run_on_queue(func): _Plugins.runOnQueue(func)
def log(data): jclass("org.telegram.messenger.FileLog").d("[DevGram] " + str(data))
def copy_to_clipboard(text): _Plugins.copyToClipboard(str(text))
def R(fn):
    interface = jclass("java.lang.Runnable")
    return type("DevGramRunnable", (dynamic_proxy(interface),), {"run": lambda self: fn()})()
def OnClickListener(fn):
    interface = jclass("android.view.View$OnClickListener")
    return type("DevGramClick", (dynamic_proxy(interface),), {"onClick": lambda self, view: fn(view)})()
def OnLongClickListener(fn):
    interface = jclass("android.view.View$OnLongClickListener")
    return type("DevGramLongClick", (dynamic_proxy(interface),), {"onLongClick": lambda self, view: bool(fn(view))})()

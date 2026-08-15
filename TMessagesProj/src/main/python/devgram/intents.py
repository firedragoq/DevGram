"""DevGram-native Android intent routing for plugins."""

import traceback
from urllib.parse import parse_qs, urlparse

from java import jclass


_handlers = []


class IntentContext:
    def __init__(self, intent):
        self.intent = intent
        self.action = str(intent.getAction() or "")
        self.uri = str(intent.getDataString() or "")
        self.parsed = urlparse(self.uri)
        self.query = parse_qs(self.parsed.query, keep_blank_values=True)


class HandlerHandle:
    def __init__(self, entry):
        self._entry = entry

    def unhandle(self):
        try:
            _handlers.remove(self._entry)
            return True
        except ValueError:
            return False


def register(callback, *, action=None, scheme=None, host=None, path=None,
             query=None, category=None, flags=0, priority=0):
    """Register an incoming-intent handler. Return True from callback to consume it."""
    entry = {
        "callback": callback,
        "action": action,
        "scheme": scheme,
        "host": host,
        "path": path,
        "query": dict(query or {}),
        "category": category,
        "flags": int(flags or 0),
        "priority": int(priority or 0),
    }
    _handlers.append(entry)
    _handlers.sort(key=lambda item: item["priority"], reverse=True)
    return HandlerHandle(entry)


handle = register


def _matches(entry, context):
    intent = context.intent
    if entry["action"] and context.action != entry["action"]:
        return False
    if entry["scheme"] and context.parsed.scheme != entry["scheme"]:
        return False
    if entry["host"] and context.parsed.hostname != entry["host"]:
        return False
    if entry["path"] and not context.parsed.path.startswith(entry["path"]):
        return False
    if entry["flags"] and (intent.getFlags() & entry["flags"]) != entry["flags"]:
        return False
    if entry["category"]:
        categories = intent.getCategories()
        if categories is None or not categories.contains(entry["category"]):
            return False
    for key, expected in entry["query"].items():
        values = context.query.get(str(key), [])
        if str(expected) not in values:
            return False
    return True


def dispatch(intent):
    context = IntentContext(intent)
    for entry in tuple(_handlers):
        if not _matches(entry, context):
            continue
        try:
            if entry["callback"](context) is True:
                return True
        except Exception:
            traceback.print_exc()
    return False


def open_uri(uri):
    """Open a URI using Android's resolver."""
    Intent = jclass("android.content.Intent")
    Uri = jclass("android.net.Uri")
    return bool(jclass("org.telegram.messenger.DevGramPlugins").openIntent(
        Intent(Intent.ACTION_VIEW, Uri.parse(str(uri)))))


def send(intent):
    """Launch an explicitly constructed Android Intent."""
    return bool(jclass("org.telegram.messenger.DevGramPlugins").openIntent(intent))

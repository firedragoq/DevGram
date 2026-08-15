# DevGram Plugin SDK

DevGram uses native `.dgplugin` packages. They are ZIP archives with this layout:

```text
my_plugin.dgplugin
|- manifest.json
|- main.py
|- locales/           # optional: en.json, ru.json
|- assets/            # optional plugin assets
|- wheels/            # optional bundled Python wheels
```

`manifest.json` must contain a stable `id`, `name`, `version`, `author`, and
`entrypoint` (normally `main.py`). The package is validated, extracted to a
private directory, and installed atomically. A failed update leaves the active
version untouched.

## Minimal plugin

```python
from devgram import BasePlugin
from devgram.ui import Header, Switch, Button
from devgram.ui.bulletin import BulletinHelper

class Example(BasePlugin):
    id = "example.plugin"
    name = "Example"
    version = "1.0.0"
    author = "Your name"

    def settings(self):
        return [
            Header(text="Example settings"),
            Switch(key="enabled", text="Enabled", default=True),
            Button(key="test", text="Show bulletin"),
        ]

    def on_setting_click(self, key):
        if key == "test":
            BulletinHelper.show_success("It works")

    def menu_items(self):
        return ["Copy message"]

    def on_menu_click(self, label, message_text, dialog_id):
        if label == "Copy message":
            self.copy(message_text)
            self.bulletin("Copied", kind="success")

    def on_update_hook(self, update_name, account, update):
        # Handle a raw Telegram update when needed.
        pass

plugin = Example()
```

## Runtime API

- `send_message`, `send_photo`, `send_file`, `send_formatted_text`;
- `edit_message`, `send_request`, `tl()` for native TL requests;
- `observe(notification_id, callback)` and `ObserverHandle.close()`;
- account-aware controllers through `devgram.client_utils`;
- `run_on_ui_thread`, `run_on_queue`, clipboard helpers and Java listeners;
- `settings()` with `Header`, `Switch`, `Input`, `Selector`, `Button` and `Text`;
- `register_pill()` / `unregister_pills()` for interactive Pill Stack widgets;
- `asset_path()` and localized `string()` resources;
- `hook()` and `java_class()` for explicitly requested native integrations.

Callbacks are isolated from the client. Exceptions are logged and do not stop
other plugins. Safe mode disables plugin callbacks and remote developer tools.

## Formatted text

Use `devgram.text_formatting` to create native Telegram entities:

```python
from devgram.client_utils import send_formatted_text
from devgram.text_formatting import bold, italic, text_url

text = "DevGram"
send_formatted_text(peer_id, text, [bold(0, 7)], account=account)
```

Entities are converted to Telegram's real `TLRPC.MessageEntity` objects before
sending, rather than being rendered as markup text.

## Development

Validate and upload a package from a connected debug build:

```bash
python3 tools/devgram_dev.py upload my_plugin.dgplugin
```

Enable DevGram developer mode first. The upload endpoint validates the archive,
uses the local developer-server token, and reloads the package without
restarting the app.

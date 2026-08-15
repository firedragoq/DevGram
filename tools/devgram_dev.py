#!/usr/bin/env python3
"""Upload a native .dgplugin archive to DevGram over ADB.

Enable "Режим разработчика" in DevGram's plugin system settings first, then
copy the token shown in "Токен dev server". The server is intentionally
reachable only through ADB forwarding and always requires that token.
"""

import argparse
import os
import subprocess
import sys
import urllib.parse
import urllib.error
import urllib.request


PORT = 42690


def adb_forward(port):
    subprocess.run(["adb", "forward", "tcp:%d" % port, "tcp:%d" % port], check=True)


def request(path, token, data=None):
    headers = {"X-DevGram-Token": token}
    if data is not None:
        headers["Content-Type"] = "application/octet-stream"
    req = urllib.request.Request("http://127.0.0.1:%d%s" % (PORT, path), data=data, headers=headers,
                                 method="POST" if data is not None else "GET")
    with urllib.request.urlopen(req, timeout=20) as response:
        return response.read().decode("utf-8", "replace")


def main():
    parser = argparse.ArgumentParser(description="DevGram local plugin development helper")
    parser.add_argument("--token", default=os.environ.get("DEVGRAM_TOKEN", ""),
                        help="token from DevGram settings (or DEVGRAM_TOKEN)")
    parser.add_argument("--no-forward", action="store_true", help="do not run adb forward")
    commands = parser.add_subparsers(dest="command", required=True)
    commands.add_parser("status", help="check the phone's local dev server")
    reload = commands.add_parser("reload", help="hot-reload all plugins or one plugin")
    reload.add_argument("--plugin", help="plugin ID to reload")
    debugger = commands.add_parser("debugger-start", help="connect DevGram to VS Code or PyCharm")
    debugger.add_argument("--platform", choices=("vscode", "pycharm"), default="vscode")
    debugger.add_argument("--host", default="127.0.0.1")
    debugger.add_argument("--port", type=int, default=5678)
    commands.add_parser("debugger-stop", help="stop the active remote debugger")
    upload = commands.add_parser("upload", help="install and reload a .dgplugin archive")
    upload.add_argument("archive", help="path to .dgplugin")
    args = parser.parse_args()

    if not args.token:
        parser.error("--token or DEVGRAM_TOKEN is required")
    try:
        if not args.no_forward:
            adb_forward(PORT)
        if args.command == "status":
            print(request("/status", args.token))
        elif args.command == "reload":
            suffix = "?" + urllib.parse.urlencode({"plugin": args.plugin}) if args.plugin else ""
            print(request("/reload" + suffix, args.token, b""))
        elif args.command == "debugger-start":
            subprocess.run(["adb", "reverse", "tcp:%d" % args.port, "tcp:%d" % args.port], check=True)
            params = urllib.parse.urlencode({"platform": args.platform, "host": args.host, "port": args.port})
            print(request("/debugger/start?" + params, args.token, b""))
            print("DevGram is connecting to %s:%d through adb reverse" % (args.host, args.port))
        elif args.command == "debugger-stop":
            print(request("/debugger/stop", args.token, b""))
        else:
            if not args.archive.endswith(".dgplugin"):
                parser.error("archive must have the .dgplugin extension")
            with open(args.archive, "rb") as archive:
                print(request("/upload", args.token, archive.read()))
    except (OSError, subprocess.CalledProcessError, urllib.error.URLError, urllib.error.HTTPError) as error:
        print("DevGram upload failed: %s" % error, file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    raise SystemExit(main())

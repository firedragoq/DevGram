#!/usr/bin/env python3
"""Upload a native .dgplugin archive to DevGram over ADB."""

import argparse
import os
import subprocess
import sys
import uuid
import urllib.error
import urllib.parse
import urllib.request


PORT = 42690


def adb_forward(port):
    subprocess.run(["adb", "forward", "tcp:%d" % port, "tcp:%d" % port], check=True)


def request(path, token, data=None, content_type=None):
    headers = {"X-DevGram-Token": token}
    if data is not None:
        headers["Content-Type"] = content_type or "application/octet-stream"
    req = urllib.request.Request(
        "http://127.0.0.1:%d%s" % (PORT, path),
        data=data,
        headers=headers,
        method="POST" if data is not None else "GET",
    )
    with urllib.request.urlopen(req, timeout=20) as response:
        return response.read().decode("utf-8", "replace")


def upload(path, token):
    """Send binary data as multipart, including compatibility with older builds."""
    boundary = "----DevGram" + uuid.uuid4().hex
    filename = os.path.basename(path).replace('"', "_")
    with open(path, "rb") as archive:
        payload = (
            ("--%s\r\n" % boundary).encode("ascii")
            + ("Content-Disposition: form-data; name=\"postData\"; filename=\"%s\"\r\n" % filename).encode("utf-8")
            + b"Content-Type: application/octet-stream\r\n\r\n"
            + archive.read()
            + ("\r\n--%s--\r\n" % boundary).encode("ascii")
        )
    return request("/upload", token, payload, "multipart/form-data; boundary=" + boundary)


def main():
    parser = argparse.ArgumentParser(description="DevGram local plugin development helper")
    parser.add_argument(
        "--token",
        default=os.environ.get("DEVGRAM_TOKEN", ""),
        help="token from DevGram settings (or DEVGRAM_TOKEN)",
    )
    parser.add_argument("--no-forward", action="store_true", help="do not run adb forward")
    commands = parser.add_subparsers(dest="command", required=True)
    commands.add_parser("status", help="check the phone's local dev server")
    reload_command = commands.add_parser("reload", help="hot-reload all plugins or one plugin")
    reload_command.add_argument("--plugin", help="plugin ID to reload")
    debugger = commands.add_parser("debugger-start", help="connect DevGram to VS Code or PyCharm")
    debugger.add_argument("--platform", choices=("vscode", "pycharm"), default="vscode")
    debugger.add_argument("--host", default="127.0.0.1")
    debugger.add_argument("--port", type=int, default=5678)
    commands.add_parser("debugger-stop", help="stop the active remote debugger")
    upload_command = commands.add_parser("upload", help="install and reload a .dgplugin archive")
    upload_command.add_argument("archive", help="path to .dgplugin")
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
            print(upload(args.archive, args.token))
    except urllib.error.HTTPError as error:
        details = error.read().decode("utf-8", "replace").strip()
        print(
            "DevGram upload failed: HTTP %s%s"
            % (error.code, ": " + details if details else ""),
            file=sys.stderr,
        )
        return 1
    except (OSError, subprocess.CalledProcessError, urllib.error.URLError) as error:
        print("DevGram upload failed: %s" % error, file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    raise SystemExit(main())

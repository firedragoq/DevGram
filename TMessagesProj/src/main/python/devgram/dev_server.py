"""Remote debugger runtime for DevGram plugin development."""

import threading


_lock = threading.RLock()
_module = None
_platform = ""
_state = "stopped"
_host = ""
_port = 0
_error = ""


def _connect(platform, host, port, wait):
    global _module, _state, _error
    try:
        if platform == "vscode":
            import debugpy
            debugpy.connect((host, port))
            _module = debugpy
            if wait:
                debugpy.wait_for_client()
        elif platform == "pycharm":
            from debugpy._vendored import force_pydevd  # noqa: F401
            import pydevd
            pydevd.settrace(host, port=port, suspend=bool(wait), stdoutToServer=True, stderrToServer=True)
            _module = pydevd
        else:
            raise ValueError("unsupported debugger platform")
        with _lock:
            _state = "connected"
    except Exception as exc:
        with _lock:
            _state = "error"
            _error = "%s: %s" % (type(exc).__name__, exc)


def start_debugger(platform="vscode", host="127.0.0.1", port=5678, wait=False):
    """Connect to a debugger exposed by the computer through adb reverse."""
    global _platform, _state, _host, _port, _error
    platform = str(platform).lower()
    host = str(host)
    port = int(port)
    if platform not in ("vscode", "pycharm"):
        raise ValueError("platform must be vscode or pycharm")
    if not host or port < 1024 or port > 65535:
        raise ValueError("invalid debugger address")
    with _lock:
        if _state in ("connecting", "connected"):
            return debugger_status()
        _platform, _host, _port, _error = platform, host, port, ""
        _state = "connecting"
        threading.Thread(target=_connect, args=(platform, host, port, wait),
                         name="DevGramDebugger", daemon=True).start()
        return debugger_status()


def stop_debugger():
    global _module, _platform, _state, _host, _port, _error
    with _lock:
        module, platform = _module, _platform
        try:
            if module is not None and platform == "vscode":
                module.disconnect()
            elif module is not None and platform == "pycharm":
                module.stoptrace()
        except Exception:
            pass
        _module = None
        _platform = _host = _error = ""
        _port = 0
        _state = "stopped"
    return True


def debugger_status():
    with _lock:
        detail = _error if _state == "error" else "%s@%s:%d" % (_platform, _host, _port) if _port else ""
        return _state + (":" + detail if detail else "")

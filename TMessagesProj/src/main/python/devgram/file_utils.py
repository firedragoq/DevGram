"""Safe filesystem helpers. Plugins should keep data inside their own directory."""
import os

def ensure_dir_exists(path): os.makedirs(path, exist_ok=True); return path
def list_dir(path, extensions=None, recursive=False, include_files=True, include_dirs=False):
    result = []
    for root, dirs, files in os.walk(path):
        if include_dirs: result.extend(os.path.join(root, name) for name in dirs)
        if include_files: result.extend(os.path.join(root, name) for name in files if not extensions or any(name.endswith(ext) for ext in extensions))
        if not recursive: break
    return result
def read_file(path):
    try:
        with open(path, encoding="utf-8") as stream: return stream.read()
    except OSError: return None
def write_file(path, content):
    ensure_dir_exists(os.path.dirname(path) or ".")
    with open(path, "w", encoding="utf-8") as stream: stream.write(str(content))
def read_file_bytes(path):
    try:
        with open(path, "rb") as stream: return stream.read()
    except OSError: return None
def write_file_bytes(path, content):
    ensure_dir_exists(os.path.dirname(path) or ".")
    with open(path, "wb") as stream: stream.write(content)
def delete_file(path):
    try: os.remove(path); return True
    except OSError: return False

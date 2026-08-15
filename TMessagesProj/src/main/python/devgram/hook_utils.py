"""Reflection helpers used by advanced DevGram plugins."""
from java import jclass
def find_class(class_name):
    try: return jclass(str(class_name))
    except Exception: return None
def _field(clazz, name):
    field = clazz.getDeclaredField(str(name)); field.setAccessible(True); return field
def get_private_field(obj, name):
    try: return _field(obj.getClass(), name).get(obj)
    except Exception: return None
def set_private_field(obj, name, value):
    try: _field(obj.getClass(), name).set(obj, value); return True
    except Exception: return False
def get_static_private_field(clazz, name):
    try: return _field(clazz, name).get(None)
    except Exception: return None
def set_static_private_field(clazz, name, value):
    try: _field(clazz, name).set(None, value); return True
    except Exception: return False

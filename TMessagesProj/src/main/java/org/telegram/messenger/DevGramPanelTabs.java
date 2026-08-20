package org.telegram.messenger;

import android.content.Context;
import android.view.View;

import com.chaquo.python.PyObject;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * DevGram: реестр вкладок плагинов в панели Эмодзи/GIF/Стикеры (EmojiView, TAB_PLUGIN).
 * Один плагин — одна вкладка (повторная регистрация переписывает предыдущую).
 */
public final class DevGramPanelTabs {

    private DevGramPanelTabs() { }

    private static final class Entry {
        final String title;
        final PyObject buildView;
        Entry(String title, PyObject buildView) { this.title = title; this.buildView = buildView; }
    }

    private static final Object lock = new Object();
    private static final Map<String, Entry> tabs = new LinkedHashMap<>();

    /** Мост Python: buildView(context) должен вернуть живой android.view.View. */
    public static void register(String pluginId, String title, PyObject buildView) {
        if (pluginId == null) return;
        synchronized (lock) {
            tabs.put(pluginId, new Entry(title == null ? "" : title, buildView));
        }
    }

    public static void unregister(String pluginId) {
        if (pluginId == null) return;
        synchronized (lock) {
            tabs.remove(pluginId);
        }
    }

    public static List<String> pluginIds() {
        synchronized (lock) {
            return new ArrayList<>(tabs.keySet());
        }
    }

    public static String title(String pluginId) {
        synchronized (lock) {
            Entry e = tabs.get(pluginId);
            return e == null ? "" : e.title;
        }
    }

    public static View buildView(String pluginId, Context context, long dialogId) {
        PyObject cb;
        synchronized (lock) {
            Entry e = tabs.get(pluginId);
            cb = e == null ? null : e.buildView;
        }
        if (cb == null) return null;
        try {
            PyObject v = cb.call(context, dialogId);
            return v == null ? null : v.toJava(View.class);
        } catch (Throwable e) {
            FileLog.e(e);
            return null;
        }
    }
}

/*
 * DevGram: мост Java <-> Python для системы плагинов (Chaquopy).
 * Плагины — .py-файлы в папке Android/data/<pkg>/files/plugins (её видно в файловом
 * менеджере — туда разработчик кладёт свои плагины). Каждый плагин — наследник
 * devgram.BasePlugin. Загрузка на старте, хуки дёргаются из клиента при событиях.
 */

package org.telegram.messenger;

import android.content.Context;

import com.chaquo.python.PyObject;
import com.chaquo.python.Python;
import com.chaquo.python.android.AndroidPlatform;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class DevGramPlugins {

    private static PyObject loaderModule;
    private static volatile boolean loaded;
    private static volatile boolean wantsUpdatesFlag; // хоть один плагин подписан на TL-апдейты

    // Защита от «кирпича»: если прошлый запуск упал — авто-безопасный режим + ошибка в буфер.
    private static volatile boolean crashHandlerInstalled;
    private static volatile boolean pendingCrashNotice; // показать уведомление о крэше, когда появится активность

    // Реестр проверенных плагинов: множество SHA-256 доверенных исходников (из облака).
    private static final String RTDB = "https://devgram-d03e4-default-rtdb.europe-west1.firebasedatabase.app";
    private static volatile java.util.Set<String> verifiedHashes = new java.util.HashSet<>();

    // Пример-плагин, который кладём при первом запуске (чтобы было что показать).
    private static final String SAMPLE = String.join("\n",
            "from devgram import BasePlugin",
            "",
            "class HelloPlugin(BasePlugin):",
            "    id = \"hello\"",
            "    name = \"Привет DevGram\"",
            "    version = \"1.0\"",
            "    author = \"DevGram\"",
            "    description = \"Пример: :love: -> сердечко в исходящих\"",
            "",
            "    def on_load(self):",
            "        self.log(\"пример-плагин загружен\")",
            "",
            "    def on_send_message(self, text):",
            "        return text.replace(\":love:\", \"❤️\").replace(\":shrug:\", \"¯\\\\_(ツ)_/¯\")",
            "");

    // Папка плагинов (внешняя files-папка — видна в файловом менеджере разработчику).
    public static File pluginsDir() {
        Context ctx = ApplicationLoader.applicationContext;
        File base = ctx.getExternalFilesDir(null);
        if (base == null) {
            base = ctx.getFilesDir();
        }
        File dir = new File(base, "plugins");
        if (!dir.exists()) {
            dir.mkdirs();
        }
        return dir;
    }

    private static PyObject loader() {
        if (loaderModule == null) {
            if (!Python.isStarted()) {
                Python.start(new AndroidPlatform(ApplicationLoader.applicationContext));
            }
            loaderModule = Python.getInstance().getModule("devgram_plugins");
        }
        return loaderModule;
    }

    // ===== Встроенный WebSocket-прокси (dgws.py) — локальный, без внешнего сервера =====
    public static boolean startWsProxy(String secretHex, String fakeTlsDomain, String host, int port) {
        try {
            if (!Python.isStarted()) {
                Python.start(new AndroidPlatform(ApplicationLoader.applicationContext));
            }
            Object r = Python.getInstance().getModule("dgws")
                    .callAttr("start", secretHex, fakeTlsDomain, host, port);
            return r != null && ((PyObject) r).toBoolean();
        } catch (Throwable e) {
            FileLog.e(e);
            return false;
        }
    }

    public static void stopWsProxy() {
        try {
            if (Python.isStarted()) {
                Python.getInstance().getModule("dgws").callAttr("stop");
            }
        } catch (Throwable e) {
            FileLog.e(e);
        }
    }

    public static String wsProxyLastError() {
        try {
            if (!Python.isStarted()) {
                return "python не запущен";
            }
            Object r = Python.getInstance().getModule("dgws").callAttr("last_error");
            return r == null ? "" : r.toString();
        } catch (Throwable e) {
            return "мост: " + e;
        }
    }

    public static boolean isWsProxyRunning() {
        try {
            if (!Python.isStarted()) {
                return false;
            }
            Object r = Python.getInstance().getModule("dgws").callAttr("is_running");
            return r != null && ((PyObject) r).toBoolean();
        } catch (Throwable e) {
            return false;
        }
    }

    // Загрузить все плагины (один раз на старте). Тяжёлое — звать в фоне.
    public static void loadAll() {
        if (loaded) {
            return;
        }
        loaded = true;

        // ---- защита от «кирпича» ----
        installCrashHandler();
        // если прошлый запуск не завершился штатно (boot_pending остался) ИЛИ был пойман краш —
        // значит приложение упало → принудительно включаем безопасный режим (хуки не привязываются)
        boolean prevCrash = prefs().getBoolean("boot_pending", false) || prefs().getBoolean("crashed", false);
        if (prevCrash) {
            setFlag("safe_mode", true);
            pendingCrashNotice = true;
        }
        prefs().edit().putBoolean("crashed", false).putBoolean("boot_pending", true).apply();

        fetchVerified();        // быстрый первый снимок реестра
        fetchBlocked();         // блок-лист запрещённых к публикации плагинов
        // Раньше держали постоянный SSE-стрим (1 соединение/устройство → упор в лимит Firebase
        // 100 одновременных). Заменили на периодический опрос — короткие GET'ы соединение не держат.
        scheduleRegistryPoll();
        initHooks();            // инициализируем Pine до загрузки плагинов (on_load может хукать)
        try {
            File dir = pluginsDir();
            // при первом запуске кладём пример
            File sample = new File(dir, "hello.py");
            if (!sample.exists() && dir.list() != null && dir.list().length == 0) {
                try {
                    java.io.FileWriter w = new java.io.FileWriter(sample);
                    w.write(SAMPLE);
                    w.close();
                } catch (Throwable ignore) {
                }
            }
            int n = loader().callAttr("load_dir", dir.getAbsolutePath()).toInt();
            applySavedEnabled();    // восстановить вкл/выкл, сохранённые пользователем
            refreshWantsUpdates();
            FileLog.d("[DevGramPlugins] загружено плагинов: " + n + " из " + dir.getAbsolutePath());
        } catch (Throwable e) {
            FileLog.e(e);
        }
    }

    // Хук исходящего текста — вернуть, возможно, изменённый текст.
    public static CharSequence onSendMessage(CharSequence text) {
        if (text == null || !loaded || isSafeMode()) {
            return text;
        }
        try {
            PyObject r = loader().callAttr("dispatch_send", text.toString());
            return r == null ? text : r.toString();
        } catch (Throwable e) {
            FileLog.e(e);
            return text;
        }
    }

    public static void onReceiveMessage(CharSequence text) {
        if (text == null || !loaded || isSafeMode()) {
            return;
        }
        try {
            loader().callAttr("dispatch_receive", text.toString());
        } catch (Throwable ignore) {
        }
    }

    // Список плагинов для менеджера: строки id␟name␟version␟author␟enabled.
    public static List<String> listPlugins() {
        List<String> res = new ArrayList<>();
        try {
            PyObject list = loader().callAttr("list_plugins");
            for (PyObject o : list.asList()) {
                res.add(o.toString());
            }
        } catch (Throwable e) {
            FileLog.e(e);
        }
        return res;
    }

    public static void setEnabled(String id, boolean enabled) {
        try {
            prefs().edit().putBoolean("enabled_" + id, enabled).apply(); // персистим состояние
            loader().callAttr("set_enabled", id, enabled);
            refreshWantsUpdates();
        } catch (Throwable ignore) {
        }
    }

    // Применить сохранённые пользователем состояния вкл/выкл к загруженным плагинам.
    private static void applySavedEnabled() {
        try {
            for (String s : listPlugins()) {
                String[] f = s.split("", -1);
                String id = f.length > 0 ? f[0] : "";
                if (id.isEmpty()) {
                    continue;
                }
                boolean en = prefs().getBoolean("enabled_" + id, true);
                if (!en) {
                    loader().callAttr("set_enabled", id, false);
                }
            }
        } catch (Throwable ignore) {
        }
    }

    // ===== настройки системы плагинов (флаги) =====

    private static android.content.SharedPreferences prefs() {
        return ApplicationLoader.applicationContext.getSharedPreferences("devgram_plugins", Context.MODE_PRIVATE);
    }

    public static boolean flag(String key, boolean def) {
        return prefs().getBoolean(key, def);
    }

    public static void setFlag(String key, boolean value) {
        prefs().edit().putBoolean(key, value).apply();
    }

    // Безопасный режим: плагины не выполняют хуки (для отладки проблемного плагина).
    public static boolean isSafeMode() {
        return flag("safe_mode", false);
    }

    public static String pythonVersion() {
        try {
            return loader().callAttr("python_version").toString();
        } catch (Throwable e) {
            return "3.11";
        }
    }

    // ===== клиентское API для плагинов (вызывается из Python) =====

    public static long myId() {
        return UserConfig.getInstance(UserConfig.selectedAccount).getClientUserId();
    }

    // Отправить текстовое сообщение в диалог.
    public static void sendMessage(long dialogId, String text) {
        if (text == null || text.isEmpty()) {
            return;
        }
        AndroidUtilities.runOnUIThread(() -> {
            try {
                int account = UserConfig.selectedAccount;
                SendMessagesHelper.SendMessageParams params = SendMessagesHelper.SendMessageParams.of(
                        text, dialogId, null, null, null, true, null, null, null, true, 0, 0, null, false);
                SendMessagesHelper.getInstance(account).sendMessage(params);
            } catch (Throwable e) {
                FileLog.e(e);
            }
        });
    }

    // Короткое всплывающее уведомление (тост).
    public static void toast(String text) {
        if (text == null) {
            return;
        }
        AndroidUtilities.runOnUIThread(() -> {
            try {
                android.widget.Toast.makeText(ApplicationLoader.applicationContext, text, android.widget.Toast.LENGTH_SHORT).show();
            } catch (Throwable ignore) {
            }
        });
    }

    // Персистентные настройки плагина (ключ-значение).
    public static String pluginGet(String pluginId, String key, String def) {
        return prefs().getString("p_" + pluginId + "_" + key, def);
    }

    public static void pluginSet(String pluginId, String key, String value) {
        prefs().edit().putString("p_" + pluginId + "_" + key, value).apply();
    }

    // Маркер отмены отправки (плагин вернул False из on_send_message).
    public static final String CANCEL = "\u0000DEVGRAM_CANCEL\u0000";

    public static String userName(long uid) {
        try {
            org.telegram.tgnet.TLRPC.User u = MessagesController.getInstance(UserConfig.selectedAccount).getUser(uid);
            return u != null ? String.valueOf(UserObject.getUserName(u)) : "";
        } catch (Throwable e) {
            return "";
        }
    }

    public static String chatName(long cid) {
        try {
            org.telegram.tgnet.TLRPC.Chat c = MessagesController.getInstance(UserConfig.selectedAccount).getChat(cid < 0 ? -cid : cid);
            return c != null && c.title != null ? c.title : "";
        } catch (Throwable e) {
            return "";
        }
    }

    // ---- android_utils: буфер обмена ----
    public static void copyToClipboard(String text) {
        AndroidUtilities.runOnUIThread(() -> {
            try {
                android.content.ClipboardManager cm = (android.content.ClipboardManager) ApplicationLoader.applicationContext.getSystemService(Context.CLIPBOARD_SERVICE);
                if (cm != null) {
                    cm.setPrimaryClip(android.content.ClipData.newPlainText("devgram", text));
                }
            } catch (Throwable ignore) {
            }
        });
    }

    public static String getClipboard() {
        try {
            android.content.ClipboardManager cm = (android.content.ClipboardManager) ApplicationLoader.applicationContext.getSystemService(Context.CLIPBOARD_SERVICE);
            if (cm != null && cm.hasPrimaryClip() && cm.getPrimaryClip() != null && cm.getPrimaryClip().getItemCount() > 0) {
                CharSequence t = cm.getPrimaryClip().getItemAt(0).getText();
                return t != null ? t.toString() : "";
            }
        } catch (Throwable ignore) {
        }
        return "";
    }

    // ---- file_utils: личная папка плагина ----
    private static File pluginDataDir(String pluginId) {
        String safe = pluginId == null ? "x" : pluginId.replaceAll("[^a-zA-Z0-9_\\-]", "_");
        File d = new File(pluginsDir().getParentFile(), "plugin_data/" + safe);
        if (!d.exists()) {
            d.mkdirs();
        }
        return d;
    }

    public static String readData(String pluginId, String name) {
        try {
            File f = new File(pluginDataDir(pluginId), name.replaceAll("[^a-zA-Z0-9_.\\-]", "_"));
            if (!f.exists() || f.length() > 8 * 1024 * 1024) {
                return "";
            }
            byte[] data = new byte[(int) f.length()];
            java.io.FileInputStream fis = new java.io.FileInputStream(f);
            int off = 0, r;
            while (off < data.length && (r = fis.read(data, off, data.length - off)) > 0) {
                off += r;
            }
            fis.close();
            return new String(data, java.nio.charset.StandardCharsets.UTF_8);
        } catch (Throwable e) {
            return "";
        }
    }

    public static void writeData(String pluginId, String name, String content) {
        try {
            File f = new File(pluginDataDir(pluginId), name.replaceAll("[^a-zA-Z0-9_.\\-]", "_"));
            java.io.FileWriter w = new java.io.FileWriter(f);
            w.write(content);
            w.close();
        } catch (Throwable e) {
            FileLog.e(e);
        }
    }

    // ---- пункты меню сообщения ----
    public static List<String> menuItems() {
        List<String> res = new ArrayList<>();
        if (!loaded || isSafeMode()) {
            return res;
        }
        try {
            PyObject list = loader().callAttr("menu_items");
            for (PyObject o : list.asList()) {
                res.add(o.toString());
            }
        } catch (Throwable e) {
            FileLog.e(e);
        }
        return res;
    }

    public static void menuClick(String pluginId, String label, String messageText, long dialogId) {
        try {
            loader().callAttr("menu_click", pluginId, label, messageText == null ? "" : messageText, dialogId);
        } catch (Throwable e) {
            FileLog.e(e);
        }
    }

    // ---- перехват сырых TL-апдейтов ----
    public static boolean wantsUpdates() {
        return loaded && !isSafeMode() && wantsUpdatesFlag;
    }

    public static void refreshWantsUpdates() {
        try {
            wantsUpdatesFlag = loader().callAttr("wants_updates").toBoolean();
        } catch (Throwable e) {
            wantsUpdatesFlag = false;
        }
    }

    public static void onUpdate(Object update) {
        try {
            loader().callAttr("dispatch_update", update);
        } catch (Throwable ignore) {
        }
    }

    // ---- диалоги / bulletins ----
    public static void bulletin(String text) {
        if (text == null) {
            return;
        }
        AndroidUtilities.runOnUIThread(() -> {
            try {
                org.telegram.ui.ActionBar.BaseFragment f = org.telegram.ui.LaunchActivity.getSafeLastFragment();
                if (f != null) {
                    org.telegram.ui.Components.BulletinFactory.of(f).createSimpleBulletin(R.raw.info, text).show();
                }
            } catch (Throwable ignore) {
            }
        });
    }

    public static void alert(String title, String message) {
        AndroidUtilities.runOnUIThread(() -> {
            try {
                org.telegram.ui.ActionBar.BaseFragment f = org.telegram.ui.LaunchActivity.getSafeLastFragment();
                android.app.Activity a = f != null ? f.getParentActivity() : null;
                if (a == null) {
                    return;
                }
                org.telegram.ui.ActionBar.AlertDialog.Builder b = new org.telegram.ui.ActionBar.AlertDialog.Builder(a);
                b.setTitle(title == null ? "" : title);
                b.setMessage(message == null ? "" : message);
                b.setPositiveButton("OK", null);
                b.show();
            } catch (Throwable e) {
                FileLog.e(e);
            }
        });
    }

    // ---- настройки плагина (UI-страница) ----
    public static List<String> pluginSettings(String pluginId) {
        List<String> res = new ArrayList<>();
        try {
            PyObject list = loader().callAttr("plugin_settings", pluginId);
            for (PyObject o : list.asList()) {
                res.add(o.toString());
            }
        } catch (Throwable e) {
            FileLog.e(e);
        }
        return res;
    }

    public static boolean hasSettings(String pluginId) {
        return !pluginSettings(pluginId).isEmpty();
    }

    public static void pluginSettingClick(String pluginId, String key) {
        try {
            loader().callAttr("plugin_setting_click", pluginId, key);
        } catch (Throwable e) {
            FileLog.e(e);
        }
    }

    // ===== защита от «кирпича» (авто-безопасный режим после краша) =====

    // Ставим глобальный перехватчик необработанных исключений: сохраняем стек в prefs,
    // помечаем краш, затем передаём управление штатному обработчику.
    private static synchronized void installCrashHandler() {
        if (crashHandlerInstalled) {
            return;
        }
        crashHandlerInstalled = true;
        final Thread.UncaughtExceptionHandler prev = Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler((t, e) -> {
            try {
                java.io.StringWriter sw = new java.io.StringWriter();
                e.printStackTrace(new java.io.PrintWriter(sw));
                prefs().edit()
                        .putBoolean("crashed", true)
                        .putString("last_crash", "DevGram — приложение упало:\n\n" + sw)
                        .apply();
            } catch (Throwable ignore) {
            }
            if (prev != null) {
                prev.uncaughtException(t, e); // штатная обработка (лог/рестарт)
            }
        });
    }

    // Зовётся из главной активности (onResume). Один раз собирает и показывает ПОЛНЫЙ отчёт о крэше
    // и с задержкой снимает boot-флаг (значит запуск прошёл успешно).
    public static void onMainResume() {
        // Автостарт-надёжность: если прокси включён, но Python-поток умер (крэш/долгий простой) —
        // переподнять локальный прокси. start() идемпотентен (если жив — no-op).
        try {
            if (DevGramProxy.isEnabled() && !isWsProxyRunning()) {
                DevGramProxy.ensureLocalStarted();
            }
        } catch (Throwable ignore) {
        }
        if (pendingCrashNotice) {
            pendingCrashNotice = false;
            // сбор отчёта тяжёлый (logcat) — в фоне, затем показ на UI
            Utilities.globalQueue.postRunnable(() -> {
                final String report = collectCrashReport();
                saveCrashReport(report);
                AndroidUtilities.runOnUIThread(() -> {
                    try {
                        copyToClipboard(report);
                    } catch (Throwable ignore) {
                    }
                    showCrashDialog(report);
                });
            });
        }
        // даём приложению 8 секунд «пожить»; если за это время упадёт — boot_pending останется
        AndroidUtilities.runOnUIThread(() -> {
            try {
                prefs().edit().putBoolean("boot_pending", false).apply();
            } catch (Throwable ignore) {
            }
        }, 8000);
    }

    // Штатный уход из приложения (onPause) — значит оно успешно поднялось, снимаем boot-флаг.
    // Краш/ANR сюда не доходит, поэтому ложных срабатываний при быстром выходе нет.
    public static void onMainPause() {
        try {
            prefs().edit().putBoolean("boot_pending", false).apply();
        } catch (Throwable ignore) {
        }
    }

    // Собрать ПОЛНЫЙ отчёт: устройство, версия, Java-стек (если был) + дамп logcat (нативный бэктрейс/ANR/логи Pine).
    private static String collectCrashReport() {
        StringBuilder sb = new StringBuilder();
        sb.append("===== DevGram: отчёт о сбое =====\n");
        try {
            sb.append("время:      ").append(new java.util.Date().toString()).append('\n');
            sb.append("устройство: ").append(android.os.Build.MANUFACTURER).append(' ').append(android.os.Build.MODEL).append('\n');
            sb.append("Android:    ").append(android.os.Build.VERSION.RELEASE).append(" (API ").append(android.os.Build.VERSION.SDK_INT).append(")\n");
            try {
                Context c = ApplicationLoader.applicationContext;
                android.content.pm.PackageInfo pi = c.getPackageManager().getPackageInfo(c.getPackageName(), 0);
                sb.append("версия:     ").append(pi.versionName).append(" (").append(pi.versionCode).append(")\n");
            } catch (Throwable ignore) {
            }
            sb.append("хуки Pine:  ").append(hooksReady ? "инициализированы" : "нет").append('\n');
            sb.append("плагины:\n");
            try {
                for (String s : listPlugins()) {
                    String[] f = s.split("", -1);
                    if (f.length > 4) {
                        sb.append("   • ").append(f[1]).append(" v").append(f[2])
                                .append(" [").append("1".equals(f[4]) ? "вкл" : "выкл").append("]\n");
                    }
                }
            } catch (Throwable ignore) {
            }
        } catch (Throwable ignore) {
        }
        String javaTrace = prefs().getString("last_crash", "");
        if (javaTrace != null && !javaTrace.isEmpty()) {
            sb.append("\n===== необработанное исключение (Java) =====\n").append(javaTrace).append('\n');
        } else {
            sb.append("\n(Java-исключения не зафиксировано — вероятно нативный сбой или ANR; смотри logcat ниже)\n");
        }
        sb.append("\n===== logcat (последние строки процесса) =====\n").append(readLogcat());
        return sb.toString();
    }

    // Дамп собственных логов процесса (там и Java FATAL EXCEPTION, и логи Pine/Chaquopy перед падением).
    private static String readLogcat() {
        try {
            Process p = Runtime.getRuntime().exec(new String[]{"logcat", "-d", "-v", "time", "-t", "700"});
            java.io.BufferedReader r = new java.io.BufferedReader(new java.io.InputStreamReader(p.getInputStream()));
            StringBuilder all = new StringBuilder();
            String line;
            while ((line = r.readLine()) != null) {
                all.append(line).append('\n');
                if (all.length() > 80000) {
                    all.append("… (обрезано)\n");
                    break;
                }
            }
            r.close();
            return all.length() == 0 ? "(logcat пуст или недоступен)" : all.toString();
        } catch (Throwable e) {
            return "logcat недоступен: " + e;
        }
    }

    // Сохранить отчёт в файл во внешней папке приложения (можно достать файловым менеджером и переслать).
    private static void saveCrashReport(String report) {
        try {
            Context c = ApplicationLoader.applicationContext;
            java.io.File dir = c.getExternalFilesDir(null);
            if (dir == null) {
                dir = c.getFilesDir();
            }
            java.io.File f = new java.io.File(dir, "devgram_crash.txt");
            java.io.FileWriter w = new java.io.FileWriter(f);
            w.write(report);
            w.close();
        } catch (Throwable ignore) {
        }
    }

    // Показать отчёт целиком в прокручиваемом выделяемом диалоге.
    public static void showCrashDialog(String report) {
        try {
            org.telegram.ui.ActionBar.BaseFragment fr = org.telegram.ui.LaunchActivity.getSafeLastFragment();
            android.app.Activity a = fr != null ? fr.getParentActivity() : null;
            if (a == null) {
                return;
            }
            android.widget.TextView tv = new android.widget.TextView(a);
            tv.setText(report);
            tv.setTextIsSelectable(true);
            tv.setTypeface(android.graphics.Typeface.MONOSPACE);
            tv.setTextSize(android.util.TypedValue.COMPLEX_UNIT_DIP, 11);
            tv.setTextColor(org.telegram.ui.ActionBar.Theme.getColor(org.telegram.ui.ActionBar.Theme.key_dialogTextBlack));
            int pad = AndroidUtilities.dp(16);
            tv.setPadding(pad, pad, pad, pad);
            android.widget.ScrollView sv = new android.widget.ScrollView(a);
            sv.addView(tv);
            org.telegram.ui.ActionBar.AlertDialog.Builder b = new org.telegram.ui.ActionBar.AlertDialog.Builder(a);
            b.setTitle("Отчёт о сбое");
            b.setView(sv);
            b.setPositiveButton("OK", null);
            b.setNeutralButton("Копировать", (d, w) -> {
                try {
                    copyToClipboard(report);
                } catch (Throwable ignore) {
                }
            });
            b.show();
        } catch (Throwable e) {
            FileLog.e(e);
        }
    }

    // Показать последний сохранённый отчёт (для кнопки в «Системе плагинов»).
    public static void showLastCrashReport() {
        try {
            Context c = ApplicationLoader.applicationContext;
            java.io.File dir = c.getExternalFilesDir(null);
            if (dir == null) {
                dir = c.getFilesDir();
            }
            java.io.File f = new java.io.File(dir, "devgram_crash.txt");
            if (!f.exists()) {
                alert("Отчёт о сбое", "Сохранённых отчётов нет — приложение не падало.");
                return;
            }
            java.io.BufferedReader r = new java.io.BufferedReader(new java.io.FileReader(f));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = r.readLine()) != null) {
                sb.append(line).append('\n');
            }
            r.close();
            showCrashDialog(sb.toString());
        } catch (Throwable e) {
            FileLog.e(e);
        }
    }

    // ===== Xposed-хуки Java-методов (Pine) =====
    private static volatile boolean hooksReady;

    public static synchronized boolean initHooks() {
        if (hooksReady) {
            return true;
        }
        try {
            top.canyie.pine.Pine.ensureInitialized();
            hooksReady = top.canyie.pine.Pine.isInitialized();
        } catch (Throwable e) {
            FileLog.e(e); // несовместимое устройство — хуки просто не работают, приложение живёт
            hooksReady = false;
        }
        return hooksReady;
    }

    // Хукнуть метод/конструктор: before_hook/after_hook плагина зовутся до/после оригинала.
    public static boolean hook(String pluginId, String className, String methodName, List<String> paramTypeNames) {
        if (isSafeMode()) {
            return false; // безопасный режим — хуки вообще не привязываем (нечему падать)
        }
        if (!initHooks()) {
            return false;
        }
        try {
            Class<?> clazz = ApplicationLoader.applicationContext.getClassLoader().loadClass(className);
            Class<?>[] pts = new Class<?>[paramTypeNames == null ? 0 : paramTypeNames.size()];
            for (int i = 0; i < pts.length; i++) {
                pts[i] = resolveType(paramTypeNames.get(i));
            }
            java.lang.reflect.Member member;
            if ("<init>".equals(methodName)) {
                java.lang.reflect.Constructor<?> c = clazz.getDeclaredConstructor(pts);
                c.setAccessible(true);
                member = c;
            } else {
                java.lang.reflect.Method m = clazz.getDeclaredMethod(methodName, pts);
                m.setAccessible(true);
                member = m;
            }
            final String pid = pluginId;
            top.canyie.pine.Pine.hook(member, new top.canyie.pine.callback.MethodHook() {
                @Override
                public void beforeCall(top.canyie.pine.Pine.CallFrame frame) {
                    dispatchHook(pid, "before", frame);
                }

                @Override
                public void afterCall(top.canyie.pine.Pine.CallFrame frame) {
                    dispatchHook(pid, "after", frame);
                }
            });
            FileLog.d("[DevGramPlugins] hook: " + className + "." + methodName);
            return true;
        } catch (Throwable e) {
            FileLog.e(e);
            return false;
        }
    }

    // Визуальный хелпер для плагинов: скруглить углы View по контуру (на UI-потоке).
    public static void roundView(final android.view.View v, final int radiusDp) {
        if (v == null) {
            return;
        }
        AndroidUtilities.runOnUIThread(() -> {
            try {
                final int r = AndroidUtilities.dp(radiusDp);
                v.setClipToOutline(true);
                v.setOutlineProvider(new android.view.ViewOutlineProvider() {
                    @Override
                    public void getOutline(android.view.View view, android.graphics.Outline outline) {
                        outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(), r);
                    }
                });
                v.invalidateOutline();
            } catch (Throwable e) {
                FileLog.e(e);
            }
        });
    }

    // ============ Жидкое стекло (Liquid Glass) ============
    // corner/blur — в dp, tint — ARGB (напр. 0x26FFFFFF), border — яркость кромки 0..1.

    private static boolean onUiThread() {
        return android.os.Looper.myLooper() == android.os.Looper.getMainLooper();
    }

    // Новая пустая стеклянная панель-контейнер. Плагин сам добавляет её куда нужно
    // (через addViewFrame) и кладёт внутрь свой контент. contextView — любая вью для
    // контекста/корня размытия (обычно frame.thisObject).
    public static android.view.View glassPanel(android.view.View contextView, int cornerDp, int blurDp, int tint, float border) {
        try {
            Context ctx = contextView != null ? contextView.getContext() : ApplicationLoader.applicationContext;
            org.telegram.ui.Components.DevGramGlassView g = new org.telegram.ui.Components.DevGramGlassView(ctx);
            g.setCornerRadiusDp(cornerDp).setBlurStrengthDp(blurDp).setTint(tint).setBorderLight(border);
            if (contextView != null) {
                g.setBlurRoot(contextView.getRootView());
            }
            return g;
        } catch (Throwable e) {
            FileLog.e(e);
            return null;
        }
    }

    // Обернуть СУЩЕСТВУЮЩУЮ вью в стекло: target вынимается из родителя и кладётся внутрь
    // стеклянной панели на его же место (тот же индекс/LayoutParams). Работает в любом
    // контейнере. Фон target гасим в прозрачный, чтобы стекло было видно. Возвращает панель.
    public static android.view.View glassWrap(final android.view.View target, final int cornerDp, final int blurDp, final int tint, final float border) {
        if (target == null) {
            return null;
        }
        final android.view.View[] out = new android.view.View[1];
        Runnable r = () -> {
            try {
                android.view.ViewParent p = target.getParent();
                if (!(p instanceof android.view.ViewGroup)) {
                    return;
                }
                if (p instanceof org.telegram.ui.Components.DevGramGlassView) {
                    out[0] = (android.view.View) p; // уже в стекле — не оборачиваем повторно
                    return;
                }
                android.view.ViewGroup parent = (android.view.ViewGroup) p;
                int idx = parent.indexOfChild(target);
                android.view.ViewGroup.LayoutParams lp = target.getLayoutParams();
                org.telegram.ui.Components.DevGramGlassView g = new org.telegram.ui.Components.DevGramGlassView(target.getContext());
                g.setCornerRadiusDp(cornerDp).setBlurStrengthDp(blurDp).setTint(tint).setBorderLight(border);
                g.setBlurRoot(target.getRootView());
                parent.removeView(target);
                g.addView(target, new android.widget.FrameLayout.LayoutParams(
                        android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                        android.view.ViewGroup.LayoutParams.MATCH_PARENT));
                parent.addView(g, idx, lp); // стекло занимает исходное место target
                try { target.setBackgroundColor(0x00000000); } catch (Throwable ignore) {}
                out[0] = g;
            } catch (Throwable e) {
                FileLog.e(e);
            }
        };
        if (onUiThread()) {
            r.run();
        } else {
            AndroidUtilities.runOnUIThread(r);
        }
        return out[0];
    }

    // Добавить child в parent с FrameLayout-параметрами. Размеры/отступы в dp;
    // ширина/высота: -1 = MATCH_PARENT, -2 = WRAP_CONTENT. gravity — как у Gravity.* (напр. 17 = center).
    public static void addViewFrame(final android.view.ViewGroup parent, final android.view.View child,
                                    final int widthDp, final int heightDp,
                                    final int leftDp, final int topDp, final int rightDp, final int bottomDp,
                                    final int gravity) {
        if (parent == null || child == null) {
            return;
        }
        Runnable r = () -> {
            try {
                int w = widthDp < 0 ? widthDp : AndroidUtilities.dp(widthDp);
                int h = heightDp < 0 ? heightDp : AndroidUtilities.dp(heightDp);
                android.widget.FrameLayout.LayoutParams lp = new android.widget.FrameLayout.LayoutParams(w, h);
                lp.leftMargin = AndroidUtilities.dp(leftDp);
                lp.topMargin = AndroidUtilities.dp(topDp);
                lp.rightMargin = AndroidUtilities.dp(rightDp);
                lp.bottomMargin = AndroidUtilities.dp(bottomDp);
                lp.gravity = gravity;
                parent.addView(child, lp);
            } catch (Throwable e) {
                FileLog.e(e);
            }
        };
        if (onUiThread()) {
            r.run();
        } else {
            AndroidUtilities.runOnUIThread(r);
        }
    }

    // Убрать вью из своего родителя.
    public static void removeView(final android.view.View v) {
        if (v == null) {
            return;
        }
        Runnable r = () -> {
            try {
                android.view.ViewParent p = v.getParent();
                if (p instanceof android.view.ViewGroup) {
                    ((android.view.ViewGroup) p).removeView(v);
                }
            } catch (Throwable e) {
                FileLog.e(e);
            }
        };
        if (onUiThread()) {
            r.run();
        } else {
            AndroidUtilities.runOnUIThread(r);
        }
    }

    // dp -> px (для расчётов из Python).
    public static int dp(float value) {
        return AndroidUtilities.dp(value);
    }

    private static Class<?> resolveType(String name) throws ClassNotFoundException {
        switch (name) {
            case "int": return int.class;
            case "long": return long.class;
            case "boolean": return boolean.class;
            case "byte": return byte.class;
            case "char": return char.class;
            case "short": return short.class;
            case "float": return float.class;
            case "double": return double.class;
            case "void": return void.class;
            default: return ApplicationLoader.applicationContext.getClassLoader().loadClass(name);
        }
    }

    private static void dispatchHook(String pluginId, String phase, Object frame) {
        if (isSafeMode()) {
            return; // безопасный режим — глушим хуки
        }
        try {
            loader().callAttr("dispatch_hook", pluginId, phase, frame);
            // Chaquopy боксит python-int как Long, а метод может ждать int/short/float и т.п.
            // Приводим аргументы (после before) и результат (после after) к нужным типам —
            // иначе Pine падает на invokeOriginalMethod: "argument has type int, got java.lang.Long".
            if (frame instanceof top.canyie.pine.Pine.CallFrame) {
                top.canyie.pine.Pine.CallFrame cf = (top.canyie.pine.Pine.CallFrame) frame;
                if ("before".equals(phase)) {
                    coerceArgs(cf);
                } else {
                    coerceResult(cf);
                }
            }
        } catch (Throwable ignore) {
        }
    }

    // Привести элементы frame.args к типам параметров хукнутого метода.
    private static void coerceArgs(top.canyie.pine.Pine.CallFrame cf) {
        try {
            Class<?>[] pts = paramTypesOf(cf.method);
            Object[] args = cf.args;
            if (pts == null || args == null) {
                return;
            }
            for (int i = 0; i < args.length && i < pts.length; i++) {
                args[i] = coerce(args[i], pts[i]);
            }
        } catch (Throwable ignore) {
        }
    }

    // Привести результат к типу возврата метода (если плагин подменил его числом «не того» бокса).
    private static void coerceResult(top.canyie.pine.Pine.CallFrame cf) {
        try {
            if (!(cf.method instanceof java.lang.reflect.Method)) {
                return;
            }
            Class<?> rt = ((java.lang.reflect.Method) cf.method).getReturnType();
            Object r = cf.getResult();
            Object c = coerce(r, rt);
            if (c != r) {
                cf.setResult(c);
            }
        } catch (Throwable ignore) {
        }
    }

    private static Class<?>[] paramTypesOf(java.lang.reflect.Member m) {
        if (m instanceof java.lang.reflect.Method) {
            return ((java.lang.reflect.Method) m).getParameterTypes();
        }
        if (m instanceof java.lang.reflect.Constructor) {
            return ((java.lang.reflect.Constructor<?>) m).getParameterTypes();
        }
        return null;
    }

    // Число любого бокса -> точный тип, который ждёт Java (int->Integer, long->Long и т.д.).
    private static Object coerce(Object v, Class<?> t) {
        if (!(v instanceof Number) || t == null) {
            return v;
        }
        Number n = (Number) v;
        if (t == int.class || t == Integer.class) return n.intValue();
        if (t == long.class || t == Long.class) return n.longValue();
        if (t == short.class || t == Short.class) return n.shortValue();
        if (t == byte.class || t == Byte.class) return n.byteValue();
        if (t == float.class || t == Float.class) return n.floatValue();
        if (t == double.class || t == Double.class) return n.doubleValue();
        return v;
    }

    // Перезагрузить все плагины из папки (без перезапуска приложения). Возвращает число плагинов.
    public static int reload() {
        try {
            loaded = true;
            int n = loader().callAttr("reload_all", pluginsDir().getAbsolutePath()).toInt();
            refreshWantsUpdates();
            return n;
        } catch (Throwable e) {
            FileLog.e(e);
            return 0;
        }
    }

    // Удалить плагин: файл + из реестра.
    public static boolean delete(String pluginId, String fileName) {
        try {
            if (fileName != null && !fileName.isEmpty()) {
                File f = new File(pluginsDir(), fileName);
                if (f.exists()) {
                    f.delete();
                }
            }
            loader().callAttr("unload_plugin", pluginId);
            return true;
        } catch (Throwable e) {
            FileLog.e(e);
            return false;
        }
    }

    // ===== реестр проверенных плагинов =====

    public static String sha256(String s) {
        try {
            java.security.MessageDigest md = java.security.MessageDigest.getInstance("SHA-256");
            byte[] d = md.digest(s.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : d) {
                sb.append(Character.forDigit((b >> 4) & 0xF, 16)).append(Character.forDigit(b & 0xF, 16));
            }
            return sb.toString();
        } catch (Throwable e) {
            return "";
        }
    }

    // Проверен ли плагин нами: хеш в облачном реестре ИЛИ плагин пришёл из канала-разработчика
    // плагинов (🧩) и был локально помечен доверенным при установке.
    public static boolean isVerified(String source) {
        if (source == null) {
            return false;
        }
        String h = sha256(source);
        return verifiedHashes.contains(h) || trustedFromChannel().contains(h);
    }

    // Локальный (не облачный) набор хешей плагинов, доверенных через канал 🧩. Переживает
    // облачную синхронизацию реестра (её fetch/stream не трогает этот набор).
    private static java.util.Set<String> channelTrusted;

    private static java.util.Set<String> trustedFromChannel() {
        if (channelTrusted == null) {
            channelTrusted = new java.util.HashSet<>(
                    ApplicationLoader.applicationContext
                            .getSharedPreferences("devgram_plugins_trusted", 0)
                            .getStringSet("hashes", new java.util.HashSet<>()));
        }
        return channelTrusted;
    }

    // Пометить плагин доверенным, потому что он опубликован в канале-разработчике (🧩).
    public static void trustFromChannel(String source) {
        if (source == null) {
            return;
        }
        java.util.Set<String> set = new java.util.HashSet<>(trustedFromChannel());
        if (set.add(sha256(source))) {
            channelTrusted = set;
            ApplicationLoader.applicationContext
                    .getSharedPreferences("devgram_plugins_trusted", 0)
                    .edit().putStringSet("hashes", set).apply();
        }
    }

    // Подтянуть реестр проверенных хешей из облака (на старте).
    public static void fetchVerified() {
        Utilities.globalQueue.postRunnable(() -> {
            try {
                java.net.HttpURLConnection c = (java.net.HttpURLConnection) new java.net.URL(RTDB + "/plugins_verified.json").openConnection();
                c.setConnectTimeout(15000);
                c.setReadTimeout(15000);
                if (c.getResponseCode() != 200) {
                    return;
                }
                java.io.BufferedReader br = new java.io.BufferedReader(new java.io.InputStreamReader(c.getInputStream(), java.nio.charset.StandardCharsets.UTF_8));
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = br.readLine()) != null) {
                    sb.append(line);
                }
                br.close();
                String json = sb.toString().trim();
                if (json.isEmpty() || "null".equals(json)) {
                    return;
                }
                org.json.JSONObject o = new org.json.JSONObject(json);
                java.util.Set<String> set = new java.util.HashSet<>();
                for (java.util.Iterator<String> it = o.keys(); it.hasNext(); ) {
                    set.add(it.next().toLowerCase());
                }
                verifiedHashes = set;
                FileLog.d("[DevGramPlugins] проверенных плагинов в реестре: " + set.size());
            } catch (Throwable e) {
                FileLog.e(e);
            }
        });
    }

    // Может ли текущий пользователь управлять реестром (вошёл как админ команды).
    public static boolean canManageVerified() {
        return DevGramBadges.getAdminToken() != null;
    }

    // Пометить проверенным (командой): пишет хеш в облако + локально (оптимистично).
    public static boolean verify(String source) {
        String token = DevGramBadges.getAdminToken();
        if (token == null || source == null) {
            return false;
        }
        String hash = sha256(source);
        java.util.Set<String> set = new java.util.HashSet<>(verifiedHashes);
        set.add(hash);
        verifiedHashes = set;
        Utilities.globalQueue.postRunnable(() ->
                httpVerified("PUT", RTDB + "/plugins_verified/" + hash + ".json?auth=" + token, "\"verified\""));
        return true;
    }

    // Снять проверку (командой): удаляет хеш из облака + локально.
    public static boolean unverify(String source) {
        String token = DevGramBadges.getAdminToken();
        if (token == null || source == null) {
            return false;
        }
        String hash = sha256(source);
        java.util.Set<String> set = new java.util.HashSet<>(verifiedHashes);
        set.remove(hash);
        verifiedHashes = set;
        Utilities.globalQueue.postRunnable(() ->
                httpVerified("DELETE", RTDB + "/plugins_verified/" + hash + ".json?auth=" + token, null));
        return true;
    }

    // ================= Каталог плагинов (облако RTDB /plugins_catalog) =================
    // Запись каталога: карточка плагина с метаданными + исходником для установки.
    public static class CatalogEntry {
        public String id = "", name = "", author = "", version = "", desc = "", icon = "", channel = "", source = "", filter = "";
    }

    public interface CatalogCallback {
        void onResult(java.util.ArrayList<CatalogEntry> entries);
    }

    // Забрать опубликованный каталог.
    public static void fetchCatalog(final CatalogCallback cb) {
        fetchEntries("plugins_catalog", cb);
    }

    // Забрать заявки на модерацию.
    public static void fetchPending(final CatalogCallback cb) {
        fetchEntries("plugins_pending", cb);
    }

    // Общий загрузчик списка CatalogEntry из узла RTDB (catalog / pending).
    private static void fetchEntries(final String node, final CatalogCallback cb) {
        Utilities.globalQueue.postRunnable(() -> {
            final java.util.ArrayList<CatalogEntry> list = new java.util.ArrayList<>();
            java.net.HttpURLConnection c = null;
            try {
                c = (java.net.HttpURLConnection) new java.net.URL(RTDB + "/" + node + ".json").openConnection();
                c.setConnectTimeout(15000);
                c.setReadTimeout(20000);
                if (c.getResponseCode() == 200) {
                    java.io.BufferedReader br = new java.io.BufferedReader(new java.io.InputStreamReader(c.getInputStream(), java.nio.charset.StandardCharsets.UTF_8));
                    StringBuilder sb = new StringBuilder();
                    String line;
                    while ((line = br.readLine()) != null) {
                        sb.append(line);
                    }
                    br.close();
                    String json = sb.toString().trim();
                    if (!json.isEmpty() && !"null".equals(json)) {
                        org.json.JSONObject obj = new org.json.JSONObject(json);
                        for (java.util.Iterator<String> it = obj.keys(); it.hasNext(); ) {
                            String key = it.next();
                            org.json.JSONObject o = obj.optJSONObject(key);
                            if (o == null) {
                                continue;
                            }
                            CatalogEntry e = new CatalogEntry();
                            e.id = o.optString("id", key);
                            e.name = o.optString("name", e.id);
                            e.author = o.optString("author", "");
                            e.version = o.optString("version", "");
                            e.desc = o.optString("desc", "");
                            e.icon = o.optString("icon", "");
                            e.channel = o.optString("channel", "");
                            e.source = o.optString("source", "");
                            e.filter = o.optString("filter", "");
                            list.add(e);
                        }
                    }
                }
            } catch (Throwable e) {
                FileLog.e(e);
            } finally {
                if (c != null) {
                    c.disconnect();
                }
            }
            AndroidUtilities.runOnUIThread(() -> cb.onResult(list));
        });
    }

    public interface BoolCallback {
        void onResult(boolean value);
    }

    // Плагин уже «в обработке», повторно публиковать незачем: на модерации (plugins_pending),
    // одобрен (plugins_catalog), отклонён (plugins_rejected) или заблокирован по файлу (plugins_blocked).
    // Лёгкая shallow-проверка по ключу (id / хеш источника), результат — на UI-потоке.
    public static void isPluginSubmitted(final String id, final String source, final BoolCallback cb) {
        if (id == null || id.trim().isEmpty()) {
            AndroidUtilities.runOnUIThread(() -> cb.onResult(false));
            return;
        }
        final String key = safeKey(id.trim());
        Utilities.globalQueue.postRunnable(() -> {
            boolean hit = nodeHasKey("plugins_pending", key)
                    || nodeHasKey("plugins_catalog", key)
                    || nodeHasKey("plugins_rejected", key)
                    || (source != null && !source.isEmpty() && nodeHasKey("plugins_blocked", sha256(source)));
            AndroidUtilities.runOnUIThread(() -> cb.onResult(hit));
        });
    }

    // true, если по пути RTDB/node/key есть значение (shallow — тянет только сам ключ, не весь узел).
    private static boolean nodeHasKey(String node, String key) {
        if (key == null || key.isEmpty()) {
            return false;
        }
        java.net.HttpURLConnection c = null;
        try {
            c = (java.net.HttpURLConnection) new java.net.URL(RTDB + "/" + node + "/" + key + ".json?shallow=true").openConnection();
            c.setConnectTimeout(12000);
            c.setReadTimeout(12000);
            if (c.getResponseCode() == 200) {
                java.io.BufferedReader br = new java.io.BufferedReader(new java.io.InputStreamReader(c.getInputStream(), java.nio.charset.StandardCharsets.UTF_8));
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = br.readLine()) != null) {
                    sb.append(line);
                }
                br.close();
                String s = sb.toString().trim();
                return !s.isEmpty() && !"null".equals(s);
            }
        } catch (Throwable e) {
            FileLog.e(e);
        } finally {
            if (c != null) {
                c.disconnect();
            }
        }
        return false;
    }

    private static org.json.JSONObject entryJson(CatalogEntry e) throws Exception {
        org.json.JSONObject o = new org.json.JSONObject();
        o.put("id", e.id);
        o.put("name", e.name);
        o.put("author", e.author);
        o.put("version", e.version);
        o.put("desc", e.desc);
        o.put("icon", e.icon);
        o.put("channel", e.channel);
        o.put("source", e.source);
        o.put("filter", e.filter == null ? "" : e.filter);
        return o;
    }

    private static String safeKey(String id) {
        return id.replaceAll("[^a-zA-Z0-9_\\-]", "_");
    }

    // Отправить плагин НА МОДЕРАЦИЮ (узел plugins_pending, запись открыта). Модератор одобрит →
    // плагин попадёт в каталог. Заблокированный файл отправить нельзя. Код: 1 — ок, 0 — ошибка, -1 — заблокирован.
    public static int publishToCatalog(CatalogEntry e) {
        if (e == null || e.id == null || e.id.isEmpty()) {
            return 0;
        }
        if (isBlocked(e.source)) {
            return -1;
        }
        try {
            final String body = entryJson(e).toString();
            final String key = safeKey(e.id);
            Utilities.globalQueue.postRunnable(() ->
                    httpVerified("PUT", RTDB + "/plugins_pending/" + key + ".json", body));
            return 1;
        } catch (Throwable ex) {
            FileLog.e(ex);
            return 0;
        }
    }

    // Одобрить заявку (модератор): перенести из pending в каталог + убрать из pending. Нужен токен.
    public static boolean approvePending(CatalogEntry e) {
        String token = DevGramBadges.getAdminToken();
        if (token == null || e == null || e.id == null || e.id.isEmpty()) {
            return false;
        }
        try {
            final String body = entryJson(e).toString();
            final String key = safeKey(e.id);
            Utilities.globalQueue.postRunnable(() -> {
                httpVerified("PUT", RTDB + "/plugins_catalog/" + key + ".json?auth=" + token, body);
                httpVerified("DELETE", RTDB + "/plugins_pending/" + key + ".json?auth=" + token, null);
                httpVerified("DELETE", RTDB + "/plugins_rejected/" + key + ".json?auth=" + token, null);
            });
            return true;
        } catch (Throwable ex) {
            FileLog.e(ex);
            return false;
        }
    }

    // Отклонить заявку (модератор): убрать из pending. block=true — ещё и заблокировать файл.
    public static boolean rejectPending(CatalogEntry e, boolean block) {
        String token = DevGramBadges.getAdminToken();
        if (token == null || e == null || e.id == null || e.id.isEmpty()) {
            return false;
        }
        final String key = safeKey(e.id);
        final String hash = (block && e.source != null) ? sha256(e.source) : null;
        if (hash != null) {
            java.util.Set<String> set = new java.util.HashSet<>(blockedHashes());
            set.add(hash);
            blocked = set;
        }
        Utilities.globalQueue.postRunnable(() -> {
            httpVerified("DELETE", RTDB + "/plugins_pending/" + key + ".json?auth=" + token, null);
            // метка «отклонён» (по id) — чтобы кнопка «Опубликовать» больше не показывалась
            httpVerified("PUT", RTDB + "/plugins_rejected/" + key + ".json?auth=" + token, "true");
            if (hash != null) {
                httpVerified("PUT", RTDB + "/plugins_blocked/" + hash + ".json?auth=" + token, "\"blocked\"");
            }
        });
        return true;
    }

    // Удалить публикацию из каталога без блокировки файла: её можно будет отправить повторно.
    public static boolean catalogDelete(String pluginId) {
        String token = DevGramBadges.getAdminToken();
        if (token == null || pluginId == null || pluginId.isEmpty()) {
            return false;
        }
        final String safe = safeKey(pluginId);
        Utilities.globalQueue.postRunnable(() ->
                httpVerified("DELETE", RTDB + "/plugins_catalog/" + safe + ".json?auth=" + token, null));
        return true;
    }

    // Удалить плагин из каталога командой + НАВСЕГДА заблокировать его файл (по хешу исходника),
    // чтобы больше нельзя было опубликовать. source — исходник удаляемого плагина.
    public static boolean catalogDeleteAndBlock(String pluginId, String source) {
        String token = DevGramBadges.getAdminToken();
        if (token == null || pluginId == null || pluginId.isEmpty()) {
            return false;
        }
        final String safe = safeKey(pluginId);
        final String hash = source == null ? null : sha256(source);
        if (hash != null) {
            java.util.Set<String> set = new java.util.HashSet<>(blockedHashes());
            set.add(hash);
            blocked = set;
        }
        Utilities.globalQueue.postRunnable(() -> {
            httpVerified("DELETE", RTDB + "/plugins_catalog/" + safe + ".json?auth=" + token, null);
            if (hash != null) {
                httpVerified("PUT", RTDB + "/plugins_blocked/" + hash + ".json?auth=" + token, "\"blocked\"");
            }
        });
        return true;
    }

    // ---- модераторы каталога (/moderators/{uid} = {"email":..,"tg":<telegram_id>}) ----
    // Модератор привязан к Telegram-ID: кнопка модерации показывается только владельцу этого ID
    // (обычные юзеры её не видят), а реальная защита записи — серверная (Firebase auth по UID).
    private static volatile java.util.Set<String> moderatorUids = new java.util.HashSet<>();
    private static volatile java.util.Set<Long> moderatorTgIds = new java.util.HashSet<>();

    // Запись модератора для UI-списка.
    public static class Moderator {
        public final String uid, email;
        public final long tg;
        public Moderator(String uid, String email, long tg) {
            this.uid = uid; this.email = email; this.tg = tg;
        }
    }

    public interface ModeratorsCallback {
        void onResult(java.util.ArrayList<Moderator> list);
    }

    public static void fetchModerators(final ModeratorsCallback cb) {
        Utilities.globalQueue.postRunnable(() -> {
            final java.util.ArrayList<Moderator> list = new java.util.ArrayList<>();
            final java.util.Set<String> uids = new java.util.HashSet<>();
            final java.util.Set<Long> tgs = new java.util.HashSet<>();
            java.net.HttpURLConnection c = null;
            try {
                c = (java.net.HttpURLConnection) new java.net.URL(RTDB + "/moderators.json").openConnection();
                c.setConnectTimeout(15000);
                c.setReadTimeout(15000);
                if (c.getResponseCode() == 200) {
                    java.io.BufferedReader br = new java.io.BufferedReader(new java.io.InputStreamReader(c.getInputStream(), java.nio.charset.StandardCharsets.UTF_8));
                    StringBuilder sb = new StringBuilder();
                    String line;
                    while ((line = br.readLine()) != null) sb.append(line);
                    br.close();
                    String json = sb.toString().trim();
                    if (!json.isEmpty() && !"null".equals(json)) {
                        org.json.JSONObject obj = new org.json.JSONObject(json);
                        for (java.util.Iterator<String> it = obj.keys(); it.hasNext(); ) {
                            String uid = it.next();
                            String email = "";
                            long tg = 0;
                            org.json.JSONObject o = obj.optJSONObject(uid);
                            if (o != null) { // новый формат {email, tg}
                                email = o.optString("email", "");
                                tg = o.optLong("tg", 0);
                            } else { // старый формат — просто email-строка
                                email = obj.optString(uid, "");
                            }
                            list.add(new Moderator(uid, email, tg));
                            uids.add(uid);
                            if (tg != 0) tgs.add(tg);
                        }
                    }
                }
                moderatorUids = uids;
                moderatorTgIds = tgs;
            } catch (Throwable e) {
                FileLog.e(e);
            } finally {
                if (c != null) c.disconnect();
            }
            if (cb != null) {
                AndroidUtilities.runOnUIThread(() -> cb.onResult(list));
            }
        });
    }

    // Может ли ВОШЕДШИЙ аккаунт модерировать (главный админ или его UID в списке модераторов).
    public static boolean isModerator() {
        if (DevGramBadges.isMainAdmin()) {
            return true;
        }
        String uid = DevGramBadges.getAdminUid();
        return uid != null && moderatorUids.contains(uid);
    }

    // Показывать ли вход в модерацию этому Telegram-аккаунту (по кэшу модераторов).
    // Главный админ (зашитая команда) — всегда; иначе — если его tg-id есть среди модераторов.
    public static boolean canSeeModeration(long myTgId) {
        if (DevGramBadges.isTeam(myTgId)) {
            return true;
        }
        return moderatorTgIds.contains(myTgId);
    }

    // Добавить модератора: создать ему Firebase-аккаунт (email/пароль) и записать в /moderators
    // объект {email, tg}. tgId — Telegram-ID модератора (чтобы кнопка показалась именно ему).
    // Только главный админ (правила RTDB не пустят чужой токен). cb: null-ошибка при успехе.
    public static void addModerator(String email, String password, long tgId, DevGramBadges.Callback cb) {
        String token = DevGramBadges.getAdminToken();
        if (token == null) {
            cb.onResult(false, "нужен вход главного админа");
            return;
        }
        DevGramBadges.signUpModerator(email, password, (uid, err) -> {
            if (uid == null) {
                cb.onResult(false, err);
                return;
            }
            final String em = email;
            final long tg = tgId;
            Utilities.globalQueue.postRunnable(() -> {
                try {
                    org.json.JSONObject o = new org.json.JSONObject();
                    o.put("email", em);
                    o.put("tg", tg);
                    httpVerified("PUT", RTDB + "/moderators/" + uid + ".json?auth=" + token, o.toString());
                } catch (Throwable ignore) {
                }
                java.util.Set<String> setU = new java.util.HashSet<>(moderatorUids);
                setU.add(uid);
                moderatorUids = setU;
                if (tg != 0) {
                    java.util.Set<Long> setT = new java.util.HashSet<>(moderatorTgIds);
                    setT.add(tg);
                    moderatorTgIds = setT;
                }
                AndroidUtilities.runOnUIThread(() -> cb.onResult(true, null));
            });
        });
    }

    // Персональная настройка модератора: получать ли уведомления о модерации (в чат-бота).
    // Хранится в /mod_notify/{uid} (true/false); серверный воркер это учитывает. Локальный кэш.
    private static volatile Boolean modNotifyCached; // null — не загружено

    public static boolean getModNotify() {
        return modNotifyCached == null || modNotifyCached; // по умолчанию включено
    }

    public static void setModNotify(boolean enabled) {
        String token = DevGramBadges.getAdminToken();
        String uid = DevGramBadges.getAdminUid();
        if (token == null || uid == null) {
            return;
        }
        modNotifyCached = enabled;
        Utilities.globalQueue.postRunnable(() ->
                httpVerified("PUT", RTDB + "/mod_notify/" + uid + ".json?auth=" + token, enabled ? "true" : "false"));
    }

    // Подтянуть текущее значение флага уведомлений для вошедшего модератора.
    public static void loadModNotify() {
        final String uid = DevGramBadges.getAdminUid();
        if (uid == null) {
            return;
        }
        Utilities.globalQueue.postRunnable(() -> {
            java.net.HttpURLConnection c = null;
            try {
                c = (java.net.HttpURLConnection) new java.net.URL(RTDB + "/mod_notify/" + uid + ".json").openConnection();
                c.setConnectTimeout(15000);
                c.setReadTimeout(15000);
                if (c.getResponseCode() == 200) {
                    java.io.BufferedReader br = new java.io.BufferedReader(new java.io.InputStreamReader(c.getInputStream(), java.nio.charset.StandardCharsets.UTF_8));
                    String s = br.readLine();
                    br.close();
                    modNotifyCached = !"false".equals(s == null ? "" : s.trim());
                }
            } catch (Throwable e) {
                FileLog.e(e);
            } finally {
                if (c != null) c.disconnect();
            }
        });
    }

    public static boolean removeModerator(String uid) {
        String token = DevGramBadges.getAdminToken();
        if (token == null || uid == null || uid.isEmpty()) {
            return false;
        }
        Utilities.globalQueue.postRunnable(() ->
                httpVerified("DELETE", RTDB + "/moderators/" + uid + ".json?auth=" + token, null));
        java.util.Set<String> set = new java.util.HashSet<>(moderatorUids);
        set.remove(uid);
        moderatorUids = set;
        return true;
    }

    // ---- блок-лист (запрещённые к публикации файлы) ----
    private static volatile java.util.Set<String> blocked = new java.util.HashSet<>();

    private static java.util.Set<String> blockedHashes() {
        return blocked;
    }

    public static boolean isBlocked(String source) {
        return source != null && blocked.contains(sha256(source));
    }

    public static void fetchBlocked() {
        Utilities.globalQueue.postRunnable(() -> {
            java.net.HttpURLConnection c = null;
            try {
                c = (java.net.HttpURLConnection) new java.net.URL(RTDB + "/plugins_blocked.json").openConnection();
                c.setConnectTimeout(15000);
                c.setReadTimeout(15000);
                if (c.getResponseCode() == 200) {
                    java.io.BufferedReader br = new java.io.BufferedReader(new java.io.InputStreamReader(c.getInputStream(), java.nio.charset.StandardCharsets.UTF_8));
                    StringBuilder sb = new StringBuilder();
                    String line;
                    while ((line = br.readLine()) != null) sb.append(line);
                    br.close();
                    String json = sb.toString().trim();
                    java.util.Set<String> set = new java.util.HashSet<>();
                    if (!json.isEmpty() && !"null".equals(json)) {
                        org.json.JSONObject obj = new org.json.JSONObject(json);
                        for (java.util.Iterator<String> it = obj.keys(); it.hasNext(); ) {
                            set.add(it.next().toLowerCase());
                        }
                    }
                    blocked = set;
                }
            } catch (Throwable e) {
                FileLog.e(e);
            } finally {
                if (c != null) c.disconnect();
            }
        });
    }

    // ---- фильтры/категории каталога (управляет команда) ----
    public interface FiltersCallback {
        void onResult(java.util.ArrayList<String> filters);
    }

    public static void fetchFilters(final FiltersCallback cb) {
        Utilities.globalQueue.postRunnable(() -> {
            final java.util.ArrayList<String> list = new java.util.ArrayList<>();
            java.net.HttpURLConnection c = null;
            try {
                c = (java.net.HttpURLConnection) new java.net.URL(RTDB + "/plugins_filters.json").openConnection();
                c.setConnectTimeout(15000);
                c.setReadTimeout(15000);
                if (c.getResponseCode() == 200) {
                    java.io.BufferedReader br = new java.io.BufferedReader(new java.io.InputStreamReader(c.getInputStream(), java.nio.charset.StandardCharsets.UTF_8));
                    StringBuilder sb = new StringBuilder();
                    String line;
                    while ((line = br.readLine()) != null) sb.append(line);
                    br.close();
                    String json = sb.toString().trim();
                    if (!json.isEmpty() && !"null".equals(json)) {
                        if (json.startsWith("[")) {
                            org.json.JSONArray array = new org.json.JSONArray(json);
                            for (int i = 0; i < array.length(); i++) {
                                String v = array.optString(i, "");
                                if (!v.isEmpty() && !list.contains(v)) list.add(v);
                            }
                        } else {
                            // Совместимость со старым объектом {hash: name}.
                            org.json.JSONObject obj = new org.json.JSONObject(json);
                            for (java.util.Iterator<String> it = obj.keys(); it.hasNext(); ) {
                                String v = obj.optString(it.next(), "");
                                if (!v.isEmpty() && !list.contains(v)) list.add(v);
                            }
                        }
                    }
                }
            } catch (Throwable e) {
                FileLog.e(e);
            } finally {
                if (c != null) c.disconnect();
            }
            AndroidUtilities.runOnUIThread(() -> cb.onResult(list));
        });
    }

    public static void addFilter(String name, Utilities.Callback<Boolean> callback) {
        String token = DevGramBadges.getAdminToken();
        if (token == null || name == null || name.trim().isEmpty()) {
            AndroidUtilities.runOnUIThread(() -> callback.run(false));
            return;
        }
        final String nm = name.trim();
        final String key = Integer.toHexString(nm.hashCode() & 0x7fffffff);
        try {
            final String body = org.json.JSONObject.quote(nm);
            Utilities.globalQueue.postRunnable(() -> {
                boolean ok = httpVerified("PUT", RTDB + "/plugins_filters/" + key + ".json?auth=" + token, body);
                AndroidUtilities.runOnUIThread(() -> callback.run(ok));
            });
        } catch (Throwable e) {
            AndroidUtilities.runOnUIThread(() -> callback.run(false));
        }
    }

    // Сохранить полный упорядоченный список категорий. JSON-массив сохраняет порядок в Firebase.
    public static void saveFilters(java.util.List<String> filters, Utilities.Callback<Boolean> callback) {
        String token = DevGramBadges.getAdminToken();
        if (token == null || filters == null) {
            AndroidUtilities.runOnUIThread(() -> callback.run(false));
            return;
        }
        org.json.JSONArray array = new org.json.JSONArray();
        java.util.HashSet<String> unique = new java.util.HashSet<>();
        for (String filter : filters) {
            String value = filter == null ? "" : filter.trim();
            if (!value.isEmpty() && unique.add(value)) array.put(value);
        }
        Utilities.globalQueue.postRunnable(() -> {
            boolean ok = httpVerified("PUT", RTDB + "/plugins_filters.json?auth=" + token, array.toString());
            AndroidUtilities.runOnUIThread(() -> callback.run(ok));
        });
    }

    public static boolean removeFilter(String name) {
        String token = DevGramBadges.getAdminToken();
        if (token == null || name == null) {
            return false;
        }
        final String key = Integer.toHexString(name.trim().hashCode() & 0x7fffffff);
        Utilities.globalQueue.postRunnable(() ->
                httpVerified("DELETE", RTDB + "/plugins_filters/" + key + ".json?auth=" + token, null));
        return true;
    }

    // Установлен ли уже плагин с таким id (по файлу в папке).
    public static boolean isInstalled(String pluginId) {
        if (pluginId == null || pluginId.isEmpty()) {
            return false;
        }
        String safe = pluginId.replaceAll("[^a-zA-Z0-9_\\-]", "_");
        return new File(pluginsDir(), safe + ".py").exists();
    }

    private static boolean httpVerified(String method, String urlStr, String body) {
        java.net.HttpURLConnection c = null;
        try {
            c = (java.net.HttpURLConnection) new java.net.URL(urlStr).openConnection();
            c.setRequestMethod(method);
            c.setConnectTimeout(15000);
            c.setReadTimeout(15000);
            if (body != null) {
                c.setDoOutput(true);
                c.getOutputStream().write(body.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            }
            int code = c.getResponseCode();
            return code >= 200 && code < 300;
        } catch (Throwable e) {
            FileLog.e(e);
            return false;
        } finally {
            if (c != null) {
                c.disconnect();
            }
        }
    }

    // Периодический опрос реестра проверенных + блок-листа (вместо постоянного SSE-стрима —
    // экономим одновременные соединения Firebase).
    private static boolean registryPollScheduled;

    private static void scheduleRegistryPoll() {
        if (registryPollScheduled) {
            return;
        }
        registryPollScheduled = true;
        final Runnable[] r = new Runnable[1];
        r[0] = () -> {
            fetchVerified();
            fetchBlocked();
            AndroidUtilities.runOnUIThread(r[0], 150000);
        };
        AndroidUtilities.runOnUIThread(r[0], 150000);
    }

    // ---- live-стрим реестра: у всех обновляется сразу, без перезахода ----
    private static Thread verifiedStreamThread;

    public static synchronized void startVerifiedStream() {
        if (verifiedStreamThread != null) {
            return;
        }
        verifiedStreamThread = new Thread(DevGramPlugins::verifiedStreamLoop, "DevGramVerifiedStream");
        verifiedStreamThread.setDaemon(true);
        verifiedStreamThread.start();
    }

    private static void verifiedStreamLoop() {
        while (true) {
            java.net.HttpURLConnection conn = null;
            try {
                conn = (java.net.HttpURLConnection) new java.net.URL(RTDB + "/plugins_verified.json").openConnection();
                conn.setRequestProperty("Accept", "text/event-stream");
                conn.setConnectTimeout(15000);
                conn.setReadTimeout(90000);
                java.io.BufferedReader br = new java.io.BufferedReader(new java.io.InputStreamReader(conn.getInputStream(), java.nio.charset.StandardCharsets.UTF_8));
                String line, event = null;
                while ((line = br.readLine()) != null) {
                    if (line.startsWith("event:")) {
                        event = line.substring(6).trim();
                    } else if (line.startsWith("data:")) {
                        handleVerifiedStream(event, line.substring(5).trim());
                    }
                }
                br.close();
            } catch (Throwable ignore) {
            } finally {
                if (conn != null) {
                    conn.disconnect();
                }
            }
            try {
                Thread.sleep(5000);
            } catch (InterruptedException e) {
                return;
            }
        }
    }

    private static void handleVerifiedStream(String event, String data) {
        if (!"put".equals(event) && !"patch".equals(event)) {
            return;
        }
        try {
            org.json.JSONObject d = new org.json.JSONObject(data);
            String path = d.optString("path", "/");
            Object payload = d.opt("data");
            java.util.Set<String> set = new java.util.HashSet<>(verifiedHashes);
            if ("/".equals(path)) {
                set.clear();
                if (payload instanceof org.json.JSONObject) {
                    org.json.JSONObject o = (org.json.JSONObject) payload;
                    for (java.util.Iterator<String> it = o.keys(); it.hasNext(); ) {
                        set.add(it.next().toLowerCase());
                    }
                }
            } else {
                String hash = (path.startsWith("/") ? path.substring(1) : path).toLowerCase();
                if (payload == null || org.json.JSONObject.NULL.equals(payload)) {
                    set.remove(hash);
                } else {
                    set.add(hash);
                }
            }
            verifiedHashes = set;
        } catch (Throwable ignore) {
        }
    }

    // Безопасно (без выполнения) распарсить метаданные плагина из исходника .plugin.
    // Возвращает id␟name␟version␟author␟description или "" если это не плагин DevGram.
    public static String parseMeta(String source) {
        if (source == null) {
            return "";
        }
        try {
            PyObject r = loader().callAttr("parse_meta", source);
            return r == null ? "" : r.toString();
        } catch (Throwable e) {
            FileLog.e(e);
            return "";
        }
    }

    // Установить плагин: сохранить исходник в папку и загрузить. enable — включить сразу.
    public static boolean install(String source, String pluginId, boolean enable) {
        if (source == null) {
            return false;
        }
        try {
            String safe = pluginId == null ? "" : pluginId.replaceAll("[^a-zA-Z0-9_\\-]", "_");
            if (safe.isEmpty()) {
                safe = "plugin_" + Math.abs(source.hashCode());
            }
            File f = new File(pluginsDir(), safe + ".py");
            java.io.FileWriter w = new java.io.FileWriter(f);
            w.write(source);
            w.close();
            loaded = true; // реестр активен
            int n = loader().callAttr("load_from_file", f.getAbsolutePath()).toInt();
            if (n > 0 && !enable && pluginId != null && !pluginId.isEmpty()) {
                loader().callAttr("set_enabled", pluginId, false);
            }
            return n > 0;
        } catch (Throwable e) {
            FileLog.e(e);
            return false;
        }
    }
}

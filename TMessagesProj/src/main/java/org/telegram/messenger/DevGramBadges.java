/*
 * DevGram: значок «команда / поддержавший / канал / официальный» рядом с именем.
 *
 * Хранилище — ОБЩЕЕ, в облаке Firebase Realtime Database (проект devgram-d03e4), узел
 * "badges": { "<dialogId>": <role> }. Так значки видят ВСЕ пользователи, а не только тот,
 * кто выдал. Айпи нашего сервера при этом не участвует вообще — приложение общается только
 * с Google (Firebase), поэтому айпи максимально скрыт.
 *
 * Читают все (правило ".read": true), пишет только команда (вход по Firebase Auth, правило
 * ".write" по admin-uid). Локальный SharedPreferences используется как КЭШ: слушатель RTDB
 * зеркалит облако в кэш, а roleOf() читает из кэша (быстро и работает офлайн). Базовая команда
 * зашита в код (TEAM_HARDCODED) — на случай пустого/недоступного облака.
 */

package org.telegram.messenger;

import android.content.SharedPreferences;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;

public class DevGramBadges {

    public static final int ROLE_TEAM = 0;       // команда проекта (пользователь)
    public static final int ROLE_SUPPORTER = 1;  // поддержавший форк (пользователь)
    public static final int ROLE_OFFICIAL = 2;   // официальный канал/ресурс DevGram
    public static final int ROLE_CHANNEL = 3;    // обычный канал (верификация DevGram)

    // Кастом-эмодзи Telegram, которые рисуются как значок рядом с именем (по document_id).
    public static final long EMOJI_TEAM_SUPPORTER = 5411424042932543123L; // ✈️ команда и поддержавшие
    public static final long EMOJI_CHANNEL        = 5413368748289598440L; // ✅ обычные каналы
    public static final long EMOJI_OFFICIAL       = 5413671801182004970L; // ✈️ официальные каналы DevGram

    // Базовая команда — зашита в код, снять из интерфейса нельзя.
    private static final HashSet<Long> TEAM_HARDCODED = new HashSet<>(Arrays.asList(
            7101191373L
    ));

    private static SharedPreferences prefs;

    private static SharedPreferences prefs() {
        if (prefs == null && ApplicationLoader.applicationContext != null) {
            prefs = ApplicationLoader.applicationContext.getSharedPreferences("devgram_badges", 0);
        }
        return prefs;
    }

    private static String key(long dialogId) {
        return Long.toString(dialogId);
    }

    // ================= облако (Firebase через REST, без SDK) =================
    // Всё общение — ТОЛЬКО с серверами Google (firebaseio.com / identitytoolkit.googleapis.com),
    // поэтому айпи нашего сервера нигде не участвует и максимально скрыт. Чтение открыто всем
    // (правило ".read": true), запись — по токену админа (вход email/пароль через Auth REST).

    // URL Realtime Database (регион europe-west1). Без завершающего слэша — ниже добавляем пути.
    private static final String RTDB_BASE = "https://devgram-d03e4-default-rtdb.europe-west1.firebasedatabase.app";
    private static final String API_KEY = "AIzaSyAj-Fq-7707X54Yr8t51mFAkJmCLEKtYoU";

    private static volatile String adminIdToken; // токен админа после входа (нужен для записи)
    private static boolean syncStarted;

    public interface Callback {
        void onResult(boolean ok, String error);
    }

    // Сообщить всему UI, что значки изменились: reloadInterface (профиль/шапка) + updateInterfaces
    // (список чатов пересобирает ячейки через DialogCell.update). Всегда на UI-потоке.
    private static void notifyBadgesChanged() {
        AndroidUtilities.runOnUIThread(() -> {
            NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.reloadInterface);
            for (int a = 0; a < UserConfig.MAX_ACCOUNT_COUNT; a++) {
                if (UserConfig.getInstance(a).isClientActivated()) {
                    NotificationCenter.getInstance(a).postNotificationName(NotificationCenter.updateInterfaces, MessagesController.UPDATE_MASK_ALL);
                }
            }
        });
    }

    // Забрать значки из облака в локальный кэш (в фоне). Вызываем на старте и при открытии
    // экрана значков / после выдачи.
    public static void syncFromCloud() {
        Utilities.globalQueue.postRunnable(() -> {
            String json = httpSend("GET", RTDB_BASE + "/badges.json", null);
            if (json == null) {
                return;
            }
            try {
                SharedPreferences p = prefs();
                if (p == null) {
                    return;
                }
                SharedPreferences.Editor ed = p.edit().clear();
                if (!"null".equals(json.trim())) {
                    JSONObject obj = new JSONObject(json);
                    for (Iterator<String> it = obj.keys(); it.hasNext(); ) {
                        String k = it.next();
                        try {
                            Long.parseLong(k); // валидируем ключ-dialogId
                            ed.putInt(k, obj.getInt(k));
                        } catch (Throwable ignore) {
                        }
                    }
                }
                ed.apply();
                notifyBadgesChanged();
            } catch (Throwable e) {
                FileLog.e(e);
            }
        });
    }

    private static Thread streamThread;

    public static void startSync() {
        if (syncStarted) {
            return;
        }
        syncStarted = true;
        syncFromCloud(); // быстрый первый снимок
        startStream();   // + живой поток: значки обновляются у ВСЕХ сразу, без перезапуска
    }

    // Живой поток изменений через Firebase REST Server-Sent Events: держим долгую connection к
    // badges.json (Accept: text/event-stream) и применяем события put/patch к кэшу. Как только
    // кому-то выдали/сняли значок — он появляется/исчезает у всех сразу. Чтение открыто, токен
    // не нужен. При обрыве — переподключаемся.
    private static synchronized void startStream() {
        if (streamThread != null) {
            return;
        }
        streamThread = new Thread(DevGramBadges::streamLoop, "DevGramBadgesStream");
        streamThread.setDaemon(true);
        streamThread.start();
    }

    private static void streamLoop() {
        while (true) {
            HttpURLConnection conn = null;
            try {
                URL url = new URL(RTDB_BASE + "/badges.json");
                conn = (HttpURLConnection) url.openConnection();
                conn.setRequestProperty("Accept", "text/event-stream");
                conn.setConnectTimeout(15000);
                conn.setReadTimeout(90000); // > keep-alive Firebase (~30с): тишина 90с → реконнект
                try (BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
                    String line, event = null;
                    while ((line = br.readLine()) != null) {
                        if (line.startsWith("event:")) {
                            event = line.substring(6).trim();
                        } else if (line.startsWith("data:")) {
                            handleStreamData(event, line.substring(5).trim());
                        }
                    }
                }
            } catch (Throwable ignore) {
                // обрыв/таймаут — переподключимся
            } finally {
                if (conn != null) {
                    conn.disconnect();
                }
            }
            try {
                Thread.sleep(5000); // пауза перед реконнектом
            } catch (InterruptedException e) {
                return;
            }
        }
    }

    private static void handleStreamData(String event, String data) {
        if (!"put".equals(event) && !"patch".equals(event)) {
            return; // keep-alive и прочее игнорируем
        }
        try {
            JSONObject d = new JSONObject(data);
            String path = d.optString("path", "/");
            Object payload = d.opt("data");
            SharedPreferences p = prefs();
            if (p == null) {
                return;
            }
            SharedPreferences.Editor ed = p.edit();
            if ("/".equals(path)) {
                // полный снимок (при подключении или полной замене)
                ed.clear();
                if (payload instanceof JSONObject) {
                    JSONObject obj = (JSONObject) payload;
                    for (Iterator<String> it = obj.keys(); it.hasNext(); ) {
                        String k = it.next();
                        putBadge(ed, k, obj.opt(k));
                    }
                }
            } else {
                // изменение одного значка: path = "/<dialogId>"
                String k = path.startsWith("/") ? path.substring(1) : path;
                int slash = k.indexOf('/');
                if (slash >= 0) {
                    k = k.substring(0, slash);
                }
                putBadge(ed, k, payload);
            }
            ed.apply();
            AndroidUtilities.runOnUIThread(() ->
                    NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.reloadInterface));
        } catch (Throwable e) {
            FileLog.e(e);
        }
    }

    private static void putBadge(SharedPreferences.Editor ed, String key, Object value) {
        try {
            Long.parseLong(key); // только валидные dialogId
        } catch (Throwable e) {
            return;
        }
        if (value == null || value == JSONObject.NULL) {
            ed.remove(key); // значок снят
        } else if (value instanceof Number) {
            ed.putInt(key, ((Number) value).intValue());
        } else {
            try {
                ed.putInt(key, Integer.parseInt(String.valueOf(value)));
            } catch (Throwable ignore) {
            }
        }
    }

    public static boolean isSignedIn() {
        return adminIdToken != null;
    }

    // Вход команды: Firebase Auth REST (email/пароль → idToken). Токен держим в памяти сессии.
    public static void signIn(String email, String password, Callback cb) {
        Utilities.globalQueue.postRunnable(() -> {
            try {
                JSONObject body = new JSONObject();
                body.put("email", email);
                body.put("password", password);
                body.put("returnSecureToken", true);
                String resp = httpSend("POST",
                        "https://identitytoolkit.googleapis.com/v1/accounts:signInWithPassword?key=" + API_KEY,
                        body.toString());
                if (resp != null) {
                    JSONObject r = new JSONObject(resp);
                    if (r.has("idToken")) {
                        adminIdToken = r.getString("idToken");
                        AndroidUtilities.runOnUIThread(() -> cb.onResult(true, null));
                        return;
                    }
                }
                AndroidUtilities.runOnUIThread(() -> cb.onResult(false, "неверный email или пароль"));
            } catch (Throwable e) {
                AndroidUtilities.runOnUIThread(() -> cb.onResult(false, e.getMessage()));
            }
        });
    }

    // Простой HTTP: GET/PUT/DELETE/POST. Возвращает тело ответа (2xx) или null.
    private static String httpSend(String method, String urlStr, String body) {
        HttpURLConnection conn = null;
        try {
            URL url = new URL(urlStr);
            conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod(method);
            conn.setConnectTimeout(15000);
            conn.setReadTimeout(15000);
            if (body != null) {
                conn.setDoOutput(true);
                conn.setRequestProperty("Content-Type", "application/json; charset=utf-8");
                try (OutputStream os = conn.getOutputStream()) {
                    os.write(body.getBytes(StandardCharsets.UTF_8));
                }
            }
            int code = conn.getResponseCode();
            boolean ok = code >= 200 && code < 300;
            InputStream is = ok ? conn.getInputStream() : conn.getErrorStream();
            StringBuilder sb = new StringBuilder();
            if (is != null) {
                try (BufferedReader br = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
                    String line;
                    while ((line = br.readLine()) != null) {
                        sb.append(line);
                    }
                }
            }
            if (ok) {
                return sb.toString();
            }
            FileLog.e("DevGramBadges http " + method + " " + code + ": " + sb);
            return null;
        } catch (Throwable e) {
            FileLog.e(e);
            return null;
        } finally {
            if (conn != null) {
                conn.disconnect();
            }
        }
    }

    // Роль по dialogId (>0 — пользователь, <0 — чат). -1 если значка нет.
    public static int roleOf(long dialogId) {
        if (dialogId > 0 && TEAM_HARDCODED.contains(dialogId)) {
            return ROLE_TEAM;
        }
        SharedPreferences p = prefs();
        if (p != null && p.contains(key(dialogId))) {
            return p.getInt(key(dialogId), -1);
        }
        return -1;
    }

    public static boolean isBadged(long dialogId) {
        return roleOf(dialogId) >= 0;
    }

    public static boolean isTeam(long userId) {
        return userId > 0 && roleOf(userId) == ROLE_TEAM;
    }

    public static boolean isSupporter(long userId) {
        return userId > 0 && roleOf(userId) == ROLE_SUPPORTER;
    }

    public static boolean isOfficialChat(long chatId) {
        return chatId > 0 && roleOf(-chatId) == ROLE_OFFICIAL;
    }

    // document_id кастом-эмодзи для значка этого диалога (0 — значка нет).
    public static long emojiIdOf(long dialogId) {
        switch (roleOf(dialogId)) {
            case ROLE_TEAM:
            case ROLE_SUPPORTER:
                return EMOJI_TEAM_SUPPORTER;
            case ROLE_CHANNEL:
                return EMOJI_CHANNEL;
            case ROLE_OFFICIAL:
                return EMOJI_OFFICIAL;
            default:
                return 0;
        }
    }

    // --- управление (экран «Значки DevGram») ---

    // Выдать значок. rawId — то, что ввёл разработчик (без знака); роль определяет тип.
    // Пишем в облако (PUT с токеном админа) — после записи перечитываем облако у себя, а у
    // остальных подтянется при следующей синхронизации. Без токена — локальный фолбэк.
    public static void grant(long rawId, int role) {
        if (rawId == 0) {
            return;
        }
        // каналы хранятся как dialogId (<0), пользователи — как id (>0)
        boolean isChannelRole = role == ROLE_OFFICIAL || role == ROLE_CHANNEL;
        final long dialogId = isChannelRole ? -Math.abs(rawId) : Math.abs(rawId);
        // оптимистично пишем локально (мгновенная обратная связь) — облако подтянется следом
        SharedPreferences p = prefs();
        if (p != null) {
            p.edit().putInt(key(dialogId), role).apply();
        }
        if (adminIdToken != null) {
            Utilities.globalQueue.postRunnable(() -> {
                httpSend("PUT", RTDB_BASE + "/badges/" + dialogId + ".json?auth=" + adminIdToken, Integer.toString(role));
                syncFromCloud();
            });
        }
    }

    public static void revoke(long dialogId) {
        SharedPreferences p = prefs();
        if (p != null) {
            p.edit().remove(key(dialogId)).apply();
        }
        if (adminIdToken != null) {
            Utilities.globalQueue.postRunnable(() -> {
                httpSend("DELETE", RTDB_BASE + "/badges/" + dialogId + ".json?auth=" + adminIdToken, null);
                syncFromCloud();
            });
        }
    }

    // Все выданные из интерфейса значки (без зашитой команды): dialogId -> роль.
    public static ArrayList<long[]> listGranted() {
        ArrayList<long[]> res = new ArrayList<>();
        SharedPreferences p = prefs();
        if (p != null) {
            for (Map.Entry<String, ?> e : p.getAll().entrySet()) {
                try {
                    long id = Long.parseLong(e.getKey());
                    int role = ((Number) e.getValue()).intValue();
                    res.add(new long[]{id, role});
                } catch (Throwable ignore) {
                }
            }
        }
        return res;
    }

    public static String roleName(int role) {
        switch (role) {
            case ROLE_TEAM: return "Команда проекта";
            case ROLE_OFFICIAL: return "Официальный канал DevGram";
            case ROLE_CHANNEL: return "Канал";
            default: return "Поддержавший";
        }
    }

    // Подпись, всплывающая по тапу на значок. Имя подставляется в начало.
    public static CharSequence badgeText(long dialogId, CharSequence name) {
        if (name == null) {
            name = "";
        }
        switch (roleOf(dialogId)) {
            case ROLE_OFFICIAL:
                return name + " является официальным ресурсом DevGram";
            case ROLE_CHANNEL:
                return name + " — канал, верифицированный DevGram";
            case ROLE_TEAM:
                return name + " — команда проекта DevGram";
            case ROLE_SUPPORTER:
            default:
                return name + " поддержал(а) разработку DevGram и получил(а) уникальный значок";
        }
    }
}

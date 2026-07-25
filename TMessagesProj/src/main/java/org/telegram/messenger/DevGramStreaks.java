/*
 * DevGram: «огоньки» (стрик) как в Snapchat/TikTok — сколько дней ПОДРЯД вы общаетесь
 * с человеком в личке. День засчитывается, если в этот календарный день ОБА написали.
 *
 * Хранилище — ОБЛАКО Firebase Realtime Database (проект devgram-d03e4), узел
 * streaks/{мой_id}/{id_собеседника} = "day,out,in,streak,streakDay". Стрик личный, поэтому
 * лежит под id владельца. Приложение общается только с Google — айпи сервера не участвует.
 * Локальный SharedPreferences — быстрый кэш для отрисовки и офлайна; облако его зеркалит.
 */

package org.telegram.messenger;

import android.content.SharedPreferences;

import org.json.JSONObject;

import org.telegram.tgnet.TLRPC;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Iterator;
import java.util.TimeZone;

public class DevGramStreaks {

    private static final String RTDB_BASE = "https://devgram-d03e4-default-rtdb.europe-west1.firebasedatabase.app";

    private static SharedPreferences prefs;
    private static boolean syncStarted;

    private static SharedPreferences prefs() {
        if (prefs == null && ApplicationLoader.applicationContext != null) {
            prefs = ApplicationLoader.applicationContext.getSharedPreferences("devgram_streaks", 0);
        }
        return prefs;
    }

    private static String key(long dialogId) {
        return Long.toString(dialogId);
    }

    // Номер календарного дня (по локальному времени).
    private static int today() {
        long now = System.currentTimeMillis();
        return (int) ((now + TimeZone.getDefault().getOffset(now)) / 86400000L);
    }

    private static boolean tracked(long dialogId, int accountId) {
        if (dialogId <= 0) {
            return false; // только приватные чаты (пользователи)
        }
        if (dialogId == UserConfig.getInstance(accountId).getClientUserId()) {
            return false; // не чат с самим собой
        }
        TLRPC.User u = MessagesController.getInstance(accountId).getUser(dialogId);
        return u == null || !u.bot; // боты не считаем
    }

    // Подтянуть свои стрики из облака в кэш (на старте приложения).
    public static void startSync() {
        if (syncStarted) {
            return;
        }
        syncStarted = true;
        Utilities.globalQueue.postRunnable(() -> {
            long myId = UserConfig.getInstance(UserConfig.selectedAccount).getClientUserId();
            if (myId == 0) {
                return;
            }
            String json = httpSend("GET", RTDB_BASE + "/streaks/" + myId + ".json", null);
            if (json == null || "null".equals(json.trim())) {
                return;
            }
            try {
                JSONObject obj = new JSONObject(json);
                SharedPreferences p = prefs();
                if (p == null) {
                    return;
                }
                SharedPreferences.Editor ed = p.edit();
                for (Iterator<String> it = obj.keys(); it.hasNext(); ) {
                    String k = it.next();
                    ed.putString(k, obj.getString(k));
                }
                ed.apply();
                AndroidUtilities.runOnUIThread(() ->
                        NotificationCenter.getInstance(UserConfig.selectedAccount).postNotificationName(NotificationCenter.updateInterfaces, MessagesController.UPDATE_MASK_NAME));
            } catch (Throwable ignore) {
            }
        });
    }

    // Зафиксировать новое сообщение. Состояние: day,out,in,streak,streakDay.
    public static void onMessage(int accountId, long dialogId, boolean outgoing) {
        if (!tracked(dialogId, accountId)) {
            return;
        }
        SharedPreferences p = prefs();
        if (p == null) {
            return;
        }
        int t = today();
        int day = t, out = 0, in = 0, streak = 0, streakDay = -100;
        String s = p.getString(key(dialogId), null);
        if (s != null) {
            try {
                String[] a = s.split(",");
                day = Integer.parseInt(a[0]);
                out = Integer.parseInt(a[1]);
                in = Integer.parseInt(a[2]);
                streak = Integer.parseInt(a[3]);
                streakDay = Integer.parseInt(a[4]);
            } catch (Throwable ignore) {
            }
        }
        if (t != day) { // новый день — сбрасываем отметки «сегодня»
            day = t;
            out = 0;
            in = 0;
        }
        if (outgoing) {
            out = 1;
        } else {
            in = 1;
        }
        boolean streakChanged = false;
        if (out == 1 && in == 1 && streakDay != t) { // сегодня написали ОБА — засчитываем день
            if (streakDay == t - 1) {
                streak += 1;
            } else {
                streak = 1;
            }
            streakDay = t;
            streakChanged = true;
        }
        String newState = day + "," + out + "," + in + "," + streak + "," + streakDay;
        if (newState.equals(s)) {
            return; // ничего не изменилось
        }
        p.edit().putString(key(dialogId), newState).apply();
        // пишем в облако (под своим id)
        long myId = UserConfig.getInstance(accountId).getClientUserId();
        Utilities.globalQueue.postRunnable(() ->
                httpSend("PUT", RTDB_BASE + "/streaks/" + myId + "/" + dialogId + ".json", "\"" + newState + "\""));
        if (streakChanged) {
            AndroidUtilities.runOnUIThread(() ->
                    NotificationCenter.getInstance(accountId).postNotificationName(NotificationCenter.updateInterfaces, MessagesController.UPDATE_MASK_NAME));
        }
    }

    // Текущий стрик диалога (0 — нет). Жив, если последний засчитанный день — вчера или сегодня.
    public static int getStreak(long dialogId) {
        SharedPreferences p = prefs();
        if (p == null || dialogId <= 0) {
            return 0;
        }
        String s = p.getString(key(dialogId), null);
        if (s == null) {
            return 0;
        }
        try {
            String[] a = s.split(",");
            int streak = Integer.parseInt(a[3]);
            int streakDay = Integer.parseInt(a[4]);
            return streakDay >= today() - 1 ? streak : 0;
        } catch (Throwable e) {
            return 0;
        }
    }

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
            return ok ? sb.toString() : null;
        } catch (Throwable e) {
            FileLog.e(e);
            return null;
        } finally {
            if (conn != null) {
                conn.disconnect();
            }
        }
    }
}

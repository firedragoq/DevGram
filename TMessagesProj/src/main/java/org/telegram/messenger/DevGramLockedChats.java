package org.telegram.messenger;

import android.content.SharedPreferences;
import android.text.TextUtils;
import android.util.Base64;

import org.telegram.tgnet.TLRPC;

import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;

// DevGram: скрытые (запароленные) чаты. Выбранные чаты прячутся из списка и открываются
// только после разблокировки (пасскод или биометрия). Хранилище привязано к user id
// аккаунта (при смене/выходе — не «утекает» на другой аккаунт).
//
// Состояние «раскрыто» (revealed) — только на сессию, в памяти: после сворачивания
// приложения/паузы снова прячем. На диск не пишется.
public final class DevGramLockedChats {

    private static final String PREFS = "devgram_locked_chats";
    private static final String KEY_IDS_PREFIX = "ids_a";      // + account + "_u" + uid
    private static final String KEY_PASS_HASH = "pass_hash";
    private static final String KEY_PASS_SALT = "pass_salt";
    private static final String KEY_BIOMETRIC = "biometric";
    private static final String KEY_HIDE_NOTIFY = "hide_notify";

    private static final Object LOCK = new Object();
    private static final java.util.HashMap<String, HashSet<Long>> caches = new java.util.HashMap<>();

    // сессионное состояние «скрытые чаты сейчас раскрыты» (не персистится)
    private static volatile boolean revealed = false;

    private DevGramLockedChats() {}

    private static SharedPreferences prefs() {
        return ApplicationLoader.applicationContext.getSharedPreferences(PREFS, 0);
    }

    private static long currentUid(int account) {
        if (account < 0 || account >= UserConfig.MAX_ACCOUNT_COUNT) return 0L;
        return UserConfig.getInstance(account).getClientUserId();
    }

    private static String keyFor(int account, long uid) {
        return KEY_IDS_PREFIX + account + "_u" + uid;
    }

    private static HashSet<Long> cache(int account) {
        long uid = currentUid(account);
        if (uid <= 0) return new HashSet<>();
        String key = keyFor(account, uid);
        HashSet<Long> c = caches.get(key);
        if (c != null) return c;
        c = new HashSet<>();
        String raw = prefs().getString(key, "");
        if (!TextUtils.isEmpty(raw)) {
            for (String part : raw.split(",")) {
                try {
                    long id = Long.parseLong(part.trim());
                    if (id != 0L) c.add(id);
                } catch (Throwable ignore) {}
            }
        }
        caches.put(key, c);
        return c;
    }

    private static void persist(int account, HashSet<Long> c) {
        long uid = currentUid(account);
        if (uid <= 0) return;
        StringBuilder sb = new StringBuilder();
        for (Long id : c) {
            if (sb.length() > 0) sb.append(',');
            sb.append(id);
        }
        prefs().edit().putString(keyFor(account, uid), sb.toString()).apply();
    }

    // ------------------------------ список ------------------------------
    public static boolean isLocked(int account, long dialogId) {
        if (dialogId == 0L) return false;
        synchronized (LOCK) {
            return cache(account).contains(dialogId);
        }
    }

    public static boolean isLocked(long dialogId) {
        return isLocked(UserConfig.selectedAccount, dialogId);
    }

    public static void setLocked(int account, long dialogId, boolean locked) {
        if (dialogId == 0L) return;
        synchronized (LOCK) {
            HashSet<Long> c = cache(account);
            if (locked) c.add(dialogId); else c.remove(dialogId);
            persist(account, c);
        }
    }

    public static ArrayList<Long> getAll(int account) {
        synchronized (LOCK) {
            return new ArrayList<>(cache(account));
        }
    }

    public static int count(int account) {
        synchronized (LOCK) {
            return cache(account).size();
        }
    }

    public static int count() {
        return count(UserConfig.selectedAccount);
    }

    public static boolean hasAny(int account) {
        return count(account) > 0;
    }

    public static void onAccountLoggedOut(int account) {
        synchronized (LOCK) {
            long uid = currentUid(account);
            String key = keyFor(account, uid);
            caches.remove(key);
            prefs().edit().remove(key).apply();
        }
    }

    // --------------------------- раскрытие (сессия) ---------------------------
    public static boolean isRevealed() {
        return revealed;
    }

    public static void setRevealed(boolean value) {
        revealed = value;
    }

    // прятать заблокированный чат? (да, если он locked и мы не в режиме «раскрыто»)
    public static boolean shouldHide(int account, long dialogId) {
        return !revealed && isLocked(account, dialogId);
    }

    // Отфильтровать скрытые чаты из списка диалогов (возвращает тот же список, если
    // прятать нечего — чтобы не плодить копии и не ломать сравнение списков).
    public static ArrayList<TLRPC.Dialog> filter(ArrayList<TLRPC.Dialog> list, int account) {
        if (revealed || list == null || list.isEmpty()) return list;
        HashSet<Long> c;
        synchronized (LOCK) {
            c = cache(account);
            if (c.isEmpty()) return list;
            c = new HashSet<>(c);
        }
        ArrayList<TLRPC.Dialog> out = null;
        for (int i = 0; i < list.size(); i++) {
            TLRPC.Dialog d = list.get(i);
            if (d != null && c.contains(d.id)) {
                if (out == null) {
                    out = new ArrayList<>(list.size());
                    for (int j = 0; j < i; j++) out.add(list.get(j));
                }
            } else if (out != null) {
                out.add(d);
            }
        }
        return out == null ? list : out;
    }

    // ------------------------------ пасскод ------------------------------
    public static boolean hasPasscode() {
        return !TextUtils.isEmpty(prefs().getString(KEY_PASS_HASH, ""));
    }

    public static void setPasscode(String pin) {
        if (TextUtils.isEmpty(pin)) {
            prefs().edit().remove(KEY_PASS_HASH).remove(KEY_PASS_SALT).apply();
            return;
        }
        byte[] salt = new byte[16];
        new SecureRandom().nextBytes(salt);
        String saltB64 = Base64.encodeToString(salt, Base64.NO_WRAP);
        prefs().edit()
                .putString(KEY_PASS_SALT, saltB64)
                .putString(KEY_PASS_HASH, hash(pin, saltB64))
                .apply();
    }

    public static boolean checkPasscode(String pin) {
        if (TextUtils.isEmpty(pin)) return false;
        String saved = prefs().getString(KEY_PASS_HASH, "");
        String salt = prefs().getString(KEY_PASS_SALT, "");
        if (TextUtils.isEmpty(saved) || TextUtils.isEmpty(salt)) return false;
        return saved.equals(hash(pin, salt));
    }

    private static String hash(String pin, String saltB64) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            md.update(Base64.decode(saltB64, Base64.NO_WRAP));
            byte[] out = md.digest(pin.getBytes("UTF-8"));
            return Base64.encodeToString(out, Base64.NO_WRAP);
        } catch (Throwable t) {
            return "";
        }
    }

    // --------------------------- настройки ---------------------------
    public static boolean biometricEnabled() {
        return prefs().getBoolean(KEY_BIOMETRIC, true);
    }

    public static void setBiometricEnabled(boolean v) {
        prefs().edit().putBoolean(KEY_BIOMETRIC, v).apply();
    }

    public static boolean hideNotifications() {
        return prefs().getBoolean(KEY_HIDE_NOTIFY, true);
    }

    public static void setHideNotifications(boolean v) {
        prefs().edit().putBoolean(KEY_HIDE_NOTIFY, v).apply();
    }
}

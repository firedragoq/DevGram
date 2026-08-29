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
    private static final String KEY_IDS_PREFIX = "ids_a";      // скрытые чаты: + account + "_u" + uid
    private static final String KEY_PROT_PREFIX = "prot_a";    // защищённые (отпечаток на вход): + account + "_u" + uid
    private static final String KEY_PASS_HASH = "pass_hash";
    private static final String KEY_PASS_SALT = "pass_salt";
    private static final String KEY_BIOMETRIC = "biometric";
    private static final String KEY_HIDE_NOTIFY = "hide_notify";
    private static final String KEY_TTL = "reveal_ttl";
    private static final String KEY_BIO_ARCHIVE = "bio_archive";
    private static final String KEY_BIO_SAVED = "bio_saved";
    private static final String KEY_BIO_SECRET = "bio_secret";
    private static final String KEY_BIO_DELETE = "bio_delete";

    // Варианты TTL раскрытия скрытых чатов
    public static final int TTL_ON_BACKGROUND = 0; // прятать при сворачивании (по умолчанию)
    public static final int TTL_1_MIN = 1;
    public static final int TTL_5_MIN = 2;
    public static final int TTL_15_MIN = 3;
    public static final int TTL_UNTIL_RESTART = 4; // до перезапуска приложения

    private static final Object LOCK = new Object();
    private static final java.util.HashMap<String, HashSet<Long>> caches = new java.util.HashMap<>();

    // сессионное состояние «скрытые чаты сейчас раскрыты» (не персистится)
    private static volatile boolean revealed = false;
    private static volatile long revealedAt = 0L;

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

    // --------------------------- раскрытие (сессия, с TTL) ---------------------------
    public static boolean isRevealed() {
        if (!revealed) return false;
        long ttlMs = ttlMillis();
        if (ttlMs > 0 && System.currentTimeMillis() - revealedAt > ttlMs) {
            revealed = false; // срок раскрытия истёк
            return false;
        }
        return true;
    }

    public static void setRevealed(boolean value) {
        revealed = value;
        revealedAt = value ? System.currentTimeMillis() : 0L;
    }

    // TTL в мс (0 = не по времени: при сворачивании или до перезапуска)
    private static long ttlMillis() {
        switch (getTtl()) {
            case TTL_1_MIN:  return 60_000L;
            case TTL_5_MIN:  return 5 * 60_000L;
            case TTL_15_MIN: return 15 * 60_000L;
            default:         return 0L;
        }
    }

    // Скрывать ли раскрытые чаты при сворачивании приложения (зависит от режима TTL)
    public static boolean hideOnBackground() {
        return getTtl() == TTL_ON_BACKGROUND;
    }

    // Через сколько мс автоматически спрятать (для таймера); 0 — не по таймеру
    public static long revealAutoHideDelay() {
        long ttlMs = ttlMillis();
        if (ttlMs <= 0) return 0L;
        long left = ttlMs - (System.currentTimeMillis() - revealedAt);
        return left > 0 ? left : 1L;
    }

    public static int getTtl() {
        return prefs().getInt(KEY_TTL, TTL_ON_BACKGROUND);
    }

    public static void setTtl(int v) {
        prefs().edit().putInt(KEY_TTL, v).apply();
    }

    // прятать заблокированный чат? (да, если он locked и мы не в режиме «раскрыто»)
    public static boolean shouldHide(int account, long dialogId) {
        return !isRevealed() && isLocked(account, dialogId);
    }

    // Отфильтровать скрытые чаты из списка диалогов (возвращает тот же список, если
    // прятать нечего — чтобы не плодить копии и не ломать сравнение списков).
    public static ArrayList<TLRPC.Dialog> filter(ArrayList<TLRPC.Dialog> list, int account) {
        if (isRevealed() || list == null || list.isEmpty()) return list;
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

    // dialog id для объекта результата поиска (User/Chat), иначе 0
    public static long dialogIdOf(Object obj) {
        if (obj instanceof TLRPC.User) return ((TLRPC.User) obj).id;
        if (obj instanceof TLRPC.Chat) return -((TLRPC.Chat) obj).id;
        return 0;
    }

    // true, если объект поиска (User/Chat) — скрытый чат и мы не в режиме «раскрыто»
    public static boolean isLockedSearchObject(Object obj, int account) {
        if (isRevealed()) return false;
        long did = dialogIdOf(obj);
        return did != 0 && isLocked(account, did);
    }

    // Отфильтровать результаты поиска (список User/Chat), убрав скрытые чаты
    public static ArrayList<Object> filterSearch(ArrayList<Object> list, int account) {
        if (isRevealed() || list == null || list.isEmpty()) return list;
        HashSet<Long> c;
        synchronized (LOCK) {
            c = cache(account);
            if (c.isEmpty()) return list;
            c = new HashSet<>(c);
        }
        ArrayList<Object> out = new ArrayList<>(list.size());
        for (Object o : list) {
            long did = dialogIdOf(o);
            if (did != 0 && c.contains(did)) continue;
            out.add(o);
        }
        return out;
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

    // ---------------- защищённые чаты (отпечаток на вход) ----------------
    private static final java.util.HashMap<String, HashSet<Long>> protCaches = new java.util.HashMap<>();

    private static String protKey(int account, long uid) {
        return KEY_PROT_PREFIX + account + "_u" + uid;
    }

    private static HashSet<Long> protCache(int account) {
        long uid = currentUid(account);
        if (uid <= 0) return new HashSet<>();
        String key = protKey(account, uid);
        HashSet<Long> c = protCaches.get(key);
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
        protCaches.put(key, c);
        return c;
    }

    public static boolean isProtected(int account, long dialogId) {
        if (dialogId == 0L) return false;
        synchronized (LOCK) {
            return protCache(account).contains(dialogId);
        }
    }

    public static void setProtected(int account, long dialogId, boolean value) {
        if (dialogId == 0L) return;
        synchronized (LOCK) {
            HashSet<Long> c = protCache(account);
            if (value) c.add(dialogId); else c.remove(dialogId);
            long uid = currentUid(account);
            if (uid <= 0) return;
            StringBuilder sb = new StringBuilder();
            for (Long id : c) {
                if (sb.length() > 0) sb.append(',');
                sb.append(id);
            }
            prefs().edit().putString(protKey(account, uid), sb.toString()).apply();
        }
    }

    // ---------------- флаги «спрашивать отпечаток» ----------------
    public static boolean bioArchive() { return prefs().getBoolean(KEY_BIO_ARCHIVE, false); }
    public static void setBioArchive(boolean v) { prefs().edit().putBoolean(KEY_BIO_ARCHIVE, v).apply(); }

    public static boolean bioSaved() { return prefs().getBoolean(KEY_BIO_SAVED, false); }
    public static void setBioSaved(boolean v) { prefs().edit().putBoolean(KEY_BIO_SAVED, v).apply(); }

    public static boolean bioSecret() { return prefs().getBoolean(KEY_BIO_SECRET, false); }
    public static void setBioSecret(boolean v) { prefs().edit().putBoolean(KEY_BIO_SECRET, v).apply(); }

    public static boolean bioBeforeDelete() { return prefs().getBoolean(KEY_BIO_DELETE, false); }
    public static void setBioBeforeDelete(boolean v) { prefs().edit().putBoolean(KEY_BIO_DELETE, v).apply(); }

    // Нужен ли отпечаток, чтобы открыть этот диалог (защищённый / секретный / «Избранное»)
    public static boolean needsBioToOpen(int account, long dialogId) {
        if (dialogId == 0L) return false;
        if (isProtected(account, dialogId)) return true;
        if (DialogObject.isEncryptedDialog(dialogId) && bioSecret()) return true;
        if (bioSaved() && dialogId == currentUid(account)) return true;
        return false;
    }
}

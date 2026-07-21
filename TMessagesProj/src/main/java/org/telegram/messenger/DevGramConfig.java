/*
 * DevGram: конфиг «режима призрака».
 * Логика портирована из AyuGram for Android (GPL v2+, © @Radolyn),
 * адаптирована под нашу базу и бренд.
 */

package org.telegram.messenger;

import android.app.Activity;
import android.content.SharedPreferences;

public class DevGramConfig {
    private static final Object sync = new Object();
    private static boolean loaded;

    public static SharedPreferences preferences;

    // ВАЖНО: значения по умолчанию заданы прямо в полях, чтобы поведение было корректным
    // даже если loadConfig() ещё не отработал (иначе булины были бы false = ghost включён,
    // сохранение выключено). loadConfig() лишь перезаписывает их из SharedPreferences.
    // true  = вести себя как обычный клиент (отправлять пакеты);
    // false = скрывать активность (режим призрака).
    public static boolean sendReadPackets = true;   // статусы прочтения
    public static boolean sendOnlinePackets = true; // статус «в сети»
    public static boolean sendUploadTyping = true;  // «печатает…» / «загружает…»

    // Скрывать рекламу: спонсорские сообщения в каналах и ботах + реклама в видеоплеере.
    public static boolean disableAds = false;

    // --- сохранение истории ---
    // По умолчанию ВЫКЛЮЧЕНЫ, как и режим призрака: мод не должен ничего менять,
    // пока пользователь сам не включит нужную функцию.
    public static boolean saveDeletedMessages = false; // сохранять удалённые сообщения
    public static boolean saveMessagesHistory = false; // сохранять историю правок
    public static boolean saveMedia = false;           // сохранять вложения удалённых
    public static boolean saveInBotChats = false;      // сохранять и в диалогах с ботами

    // --- гейт для разрешённых пакетов чтения (например, ручная отметка «прочитано») ---
    private static final Object readSync = new Object();
    private static boolean allowReadVal;
    private static int allowReadResetAfter;

    static {
        loadConfig();
    }

    public static void loadConfig() {
        synchronized (sync) {
            if (loaded) {
                return;
            }
            if (ApplicationLoader.applicationContext == null) {
                return; // контекст ещё не готов — попробуем позже, поля пока держат дефолты
            }
            preferences = ApplicationLoader.applicationContext.getSharedPreferences("devgram_ghost", Activity.MODE_PRIVATE);
            sendReadPackets = preferences.getBoolean("sendReadPackets", true);
            sendOnlinePackets = preferences.getBoolean("sendOnlinePackets", true);
            sendUploadTyping = preferences.getBoolean("sendUploadTyping", true);
            disableAds = preferences.getBoolean("disableAds", false);
            saveDeletedMessages = preferences.getBoolean("saveDeletedMessages", false);
            saveMessagesHistory = preferences.getBoolean("saveMessagesHistory", false);
            saveMedia = preferences.getBoolean("saveMedia", false);
            saveInBotChats = preferences.getBoolean("saveInBotChats", false);
            loaded = true;
        }
    }

    // Режим призрака активен, когда скрыты все три индикатора активности.
    public static boolean isGhostModeActive() {
        return !sendReadPackets && !sendOnlinePackets && !sendUploadTyping;
    }

    public static void setGhostMode(boolean enabled) {
        sendReadPackets = !enabled;
        sendOnlinePackets = !enabled;
        sendUploadTyping = !enabled;
        preferences.edit()
                .putBoolean("sendReadPackets", sendReadPackets)
                .putBoolean("sendOnlinePackets", sendOnlinePackets)
                .putBoolean("sendUploadTyping", sendUploadTyping)
                .apply();
    }

    public static void toggleGhostMode() {
        setGhostMode(!isGhostModeActive());
    }

    public static void setSendReadPackets(boolean v) {
        sendReadPackets = v;
        preferences.edit().putBoolean("sendReadPackets", v).apply();
    }

    public static void setSendOnlinePackets(boolean v) {
        sendOnlinePackets = v;
        preferences.edit().putBoolean("sendOnlinePackets", v).apply();
    }

    public static void setSendUploadTyping(boolean v) {
        sendUploadTyping = v;
        preferences.edit().putBoolean("sendUploadTyping", v).apply();
    }

    public static void setDisableAds(boolean v) {
        disableAds = v;
        preferences.edit().putBoolean("disableAds", v).apply();
    }

    public static void setSaveDeletedMessages(boolean v) {
        saveDeletedMessages = v;
        preferences.edit().putBoolean("saveDeletedMessages", v).apply();
    }

    public static void setSaveMessagesHistory(boolean v) {
        saveMessagesHistory = v;
        preferences.edit().putBoolean("saveMessagesHistory", v).apply();
    }

    public static void setSaveMedia(boolean v) {
        saveMedia = v;
        preferences.edit().putBoolean("saveMedia", v).apply();
    }

    public static void setSaveInBotChats(boolean v) {
        saveInBotChats = v;
        preferences.edit().putBoolean("saveInBotChats", v).apply();
    }

    // Пометка удалённого/изменённого сообщения в строке времени.
    public static String getDeletedMark() {
        return "🗑";
    }

    public static String getEditedMark() {
        return LocaleController.getString("EditedMessage", R.string.EditedMessage);
    }

    // --- read gate ---
    public static void setAllowReadPacket(boolean val, int resetAfter) {
        synchronized (readSync) {
            allowReadVal = val;
            allowReadResetAfter = resetAfter;
        }
    }

    public static boolean getAllowReadPacket() {
        if (sendReadPackets) {
            return true;
        }
        synchronized (readSync) {
            if (allowReadResetAfter == -1) {
                return allowReadVal;
            }
            if (allowReadResetAfter > 0) {
                allowReadResetAfter -= 1;
                boolean cur = allowReadVal;
                if (allowReadResetAfter == 0) {
                    allowReadVal = false;
                }
                return cur;
            }
            return allowReadVal;
        }
    }
}

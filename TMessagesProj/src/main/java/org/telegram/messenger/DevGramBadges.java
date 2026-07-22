/*
 * DevGram: значок «команда / поддержал форк / официальный» рядом с именем.
 *
 * Идея как в exteraGram: у части пользователей и чатов рядом с названием рисуется наш
 * значок (в списке чатов, в шапке, в профиле), по тапу — всплывающая подпись.
 *
 * Выдача — из самого приложения (экран «Значки DevGram», доступен команде): id и роль
 * хранятся в SharedPreferences, ничего пересобирать не нужно. Базовая команда зашита
 * в код (TEAM_HARDCODED) — её нельзя снять из интерфейса, чтобы не потерять доступ.
 * Значок пока временный — галочка; когда доведём эмодзи-самолёт, поменяем только иконку.
 */

package org.telegram.messenger;

import android.content.SharedPreferences;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Map;

public class DevGramBadges {

    public static final int ROLE_TEAM = 0;       // команда проекта (пользователь)
    public static final int ROLE_SUPPORTER = 1;  // поддержавший форк (пользователь)
    public static final int ROLE_OFFICIAL = 2;   // официальный чат/канал

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

    // --- управление (экран «Значки DevGram») ---

    // Выдать значок. rawId — то, что ввёл разработчик (без знака); роль определяет тип.
    public static void grant(long rawId, int role) {
        SharedPreferences p = prefs();
        if (p == null || rawId == 0) {
            return;
        }
        // официальный чат хранится как dialogId (<0), пользователь — как id (>0)
        long dialogId = role == ROLE_OFFICIAL ? -Math.abs(rawId) : Math.abs(rawId);
        p.edit().putInt(key(dialogId), role).apply();
    }

    public static void revoke(long dialogId) {
        SharedPreferences p = prefs();
        if (p != null) {
            p.edit().remove(key(dialogId)).apply();
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
            case ROLE_OFFICIAL: return "Официальный чат";
            default: return "Поддержавший";
        }
    }

    // Подпись, всплывающая по тапу на значок. Имя подставляется в начало.
    public static CharSequence badgeText(long dialogId, CharSequence name) {
        if (name == null) {
            name = "";
        }
        int role = roleOf(dialogId);
        if (role == ROLE_OFFICIAL || dialogId < 0) {
            return name + " получил(а) верификацию от DevGram";
        }
        if (role == ROLE_TEAM) {
            return name + " — команда проекта DevGram";
        }
        return name + " поддержал(а) разработку DevGram и получил(а) уникальный значок";
    }
}

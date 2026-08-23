package org.telegram.messenger;

import org.telegram.ui.LauncherIconController;
import org.telegram.ui.LauncherIconController.LauncherIcon;

import java.util.ArrayList;
import java.util.List;

// DevGram: маскировка приложения — подмена иконки И названия в списке приложений
// (например, DevGram выглядит как «Калькулятор»). Реализовано поверх штатного
// механизма activity-alias: у маскировочного alias свой android:label и android:icon,
// LauncherIconController включает нужный alias и гасит остальные.
//
// Добавить новую маску: 1) иконка (drawable/mipmap + adaptive), 2) activity-alias в
// манифесте с android:label и android:enabled="false", 3) запись в enum LauncherIcon
// с disguise=true (ключ = имя alias). Здесь править ничего не нужно — маски берутся
// из enum по флагу disguise.
public class DevGramDisguise {

    // Все доступные маскировки (иконки с флагом disguise).
    public static List<LauncherIcon> masks() {
        List<LauncherIcon> result = new ArrayList<>();
        for (LauncherIcon icon : LauncherIcon.values()) {
            if (icon.disguise) {
                result.add(icon);
            }
        }
        return result;
    }

    // Текущая активная иконка (маска или обычная).
    public static LauncherIcon current() {
        for (LauncherIcon icon : LauncherIcon.values()) {
            if (LauncherIconController.isEnabled(icon)) {
                return icon;
            }
        }
        return LauncherIcon.DEFAULT;
    }

    // Включена ли маскировка (иконка ≠ обычная DevGram).
    public static boolean isDisguised() {
        LauncherIcon cur = current();
        return cur != null && cur.disguise;
    }

    // Применить маску (иконка + имя меняются в лаунчере).
    public static void apply(LauncherIcon mask) {
        if (mask == null) {
            clear();
            return;
        }
        LauncherIconController.setIcon(mask);
    }

    // Снять маскировку — вернуть обычный DevGram.
    public static void clear() {
        LauncherIconController.setIcon(LauncherIcon.DEFAULT);
    }
}

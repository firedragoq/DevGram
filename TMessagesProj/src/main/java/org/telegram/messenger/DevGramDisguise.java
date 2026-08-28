package org.telegram.messenger;

import android.app.PendingIntent;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ShortcutInfo;
import android.content.pm.ShortcutManager;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.drawable.AdaptiveIconDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.Icon;
import android.os.Build;

import org.telegram.ui.LaunchActivity;
import org.telegram.ui.LauncherIconController;
import org.telegram.ui.LauncherIconController.LauncherIcon;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

// DevGram: маскировка приложения — DevGram выглядит в лаунчере как «Калькулятор»/«Часы»/…
//
// Иконку приложения в лаунчере Android НЕЛЬЗЯ подменить в рантайме на произвольную
// картинку (android:icon у activity-alias — статический ресурс APK). Чтобы иконка была
// РЕАЛЬНОЙ с телефона (1-в-1 с настоящим приложением любого вендора/темы), используем
// закреплённый ярлык (pinned shortcut): берём иконку и имя установленного приложения-
// аналога через PackageManager и создаём ярлык, запускающий DevGram. После закрепления
// прячем оригинальную иконку DevGram — в лаунчере остаётся только «маска».
//
// Состояние маскировки храним в prefs (все alias при этом выключены, по ним не определить).
public class DevGramDisguise {

    public static final String SHORTCUT_ID = "devgram_disguise_mask";
    private static final String PREFS = "devgram_disguise";
    private static final String KEY_MASK = "mask"; // ключ активной маски или ""

    private static SharedPreferences prefs() {
        return ApplicationLoader.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

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

    private static LauncherIcon byKey(String key) {
        if (key != null && key.length() > 0) {
            for (LauncherIcon icon : LauncherIcon.values()) {
                if (icon.key.equals(key)) {
                    return icon;
                }
            }
        }
        return LauncherIcon.DEFAULT;
    }

    // Текущая активная маска (или DEFAULT).
    public static LauncherIcon current() {
        return byKey(prefs().getString(KEY_MASK, ""));
    }

    // Включена ли маскировка.
    public static boolean isDisguised() {
        LauncherIcon cur = current();
        return cur != null && cur.disguise;
    }

    // Применить маску. Для API 26+ — через закреплённый ярлык с реальной иконкой
    // (система покажет диалог подтверждения; активация завершится в receiver'е после
    // фактического закрепления). Для более старых — старый способ со статической иконкой.
    public static void apply(Context ctx, LauncherIcon mask) {
        if (mask == null) {
            clear();
            return;
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && requestPinShortcut(ctx, mask)) {
            return; // ждём подтверждения пользователя → onShortcutPinned()
        }
        // fallback (API <26 или ярлыки не поддерживаются лаунчером): статическая иконка-alias
        prefs().edit().putString(KEY_MASK, mask.key).apply();
        LauncherIconController.setIcon(mask);
    }

    // Снять маскировку — вернуть обычный DevGram и погасить ярлык-маску.
    public static void clear() {
        Context ctx = ApplicationLoader.applicationContext;
        prefs().edit().putString(KEY_MASK, "").apply();
        LauncherIconController.setIcon(LauncherIcon.DEFAULT); // вернуть иконку в лаунчер
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            try {
                ShortcutManager sm = ctx.getSystemService(ShortcutManager.class);
                if (sm != null) {
                    // pinned-ярлык программно не удалить — делаем его неактивным (серым),
                    // пользователь уберёт его с рабочего стола сам.
                    sm.disableShortcuts(Collections.singletonList(SHORTCUT_ID),
                            LocaleController.getString(R.string.DevGramDisguiseNone));
                }
            } catch (Exception ignore) {
            }
        }
    }

    // Запросить закрепление ярлыка-маски. true — запрос отправлен.
    private static boolean requestPinShortcut(Context ctx, LauncherIcon mask) {
        try {
            ShortcutManager sm = ctx.getSystemService(ShortcutManager.class);
            if (sm == null || !sm.isRequestPinShortcutSupported()) {
                return false;
            }
            Drawable d = realIcon(ctx, mask);
            Icon icon = d != null ? iconFromDrawable(d) : Icon.createWithResource(ctx, previewRes(mask));
            CharSequence label = realLabel(ctx, mask);

            Intent launch = new Intent(Intent.ACTION_MAIN);
            launch.setComponent(new ComponentName(ctx, LaunchActivity.class));
            launch.addCategory(Intent.CATEGORY_LAUNCHER);

            ShortcutInfo info = new ShortcutInfo.Builder(ctx, SHORTCUT_ID)
                    .setShortLabel(label)
                    .setLongLabel(label)
                    .setIcon(icon)
                    .setIntent(launch)
                    .build();

            Intent cb = new Intent(ctx, DevGramDisguiseReceiver.class);
            cb.setAction(DevGramDisguiseReceiver.ACTION_PINNED);
            cb.putExtra("mask", mask.key);
            int flags = PendingIntent.FLAG_UPDATE_CURRENT;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                flags |= PendingIntent.FLAG_IMMUTABLE;
            }
            PendingIntent pi = PendingIntent.getBroadcast(ctx, 0, cb, flags);
            return sm.requestPinShortcut(info, pi.getIntentSender());
        } catch (Exception e) {
            FileLog.e(e);
            return false;
        }
    }

    // Вызывается из DevGramDisguiseReceiver, когда ярлык РЕАЛЬНО закреплён:
    // фиксируем маску и прячем оригинальную иконку DevGram из лаунчера.
    public static void onShortcutPinned(Context ctx, String maskKey) {
        prefs().edit().putString(KEY_MASK, maskKey == null ? "" : maskKey).apply();
        hideLauncherIcon(ctx);
    }

    // Погасить все launcher-иконки приложения (в лаунчере останется только ярлык-маска;
    // запуск самого DevGram по ярлыку идёт напрямую через LaunchActivity, ей alias не нужен).
    public static void hideLauncherIcon(Context ctx) {
        PackageManager pm = ctx.getPackageManager();
        for (LauncherIcon i : LauncherIcon.values()) {
            try {
                pm.setComponentEnabledSetting(i.getComponentName(ctx),
                        PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                        PackageManager.DONT_KILL_APP);
            } catch (Exception ignore) {
            }
        }
    }

    // Icon из Drawable реального приложения. Для adaptive-иконок сохраняем «adaptive»
    // (лаунчер применит СВОЮ маску формы — как ко всем иконкам, кружок/squircle 1-в-1).
    private static Icon iconFromDrawable(Drawable d) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && d instanceof AdaptiveIconDrawable) {
            int size = AndroidUtilities.dp(108);
            return Icon.createWithAdaptiveBitmap(drawableToBitmap(d, size));
        }
        int size = d.getIntrinsicWidth() > 0 ? d.getIntrinsicWidth() : AndroidUtilities.dp(48);
        return Icon.createWithBitmap(drawableToBitmap(d, size));
    }

    private static Bitmap drawableToBitmap(Drawable d, int size) {
        Bitmap bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bmp);
        d.setBounds(0, 0, size, size);
        d.draw(canvas);
        return bmp;
    }

    // Наша заготовка-иконка (цельный drawable) для превью, когда реального приложения нет.
    public static int previewRes(LauncherIcon mask) {
        switch (mask.key) {
            case "CalculatorIcon": return R.drawable.devgram_mask_calc_full;
            case "NotesIcon":      return R.drawable.devgram_mask_notes_full;
            case "WeatherIcon":    return R.drawable.devgram_mask_weather_full;
            case "ClockIcon":      return R.drawable.devgram_mask_clock_full;
            case "SettingsIcon":   return R.drawable.devgram_mask_settings_full;
            default:               return R.mipmap.icon_01_launcher;
        }
    }

    // Известные пакеты реальных приложений на телефоне для каждой маски — чтобы взять
    // иконку/имя ИМЕННО так, как они выглядят у пользователя (Google/Samsung/MIUI/…).
    private static String[] systemPackagesFor(LauncherIcon mask) {
        switch (mask.key) {
            case "CalculatorIcon":
                return new String[]{"com.google.android.calculator", "com.android.calculator2",
                        "com.sec.android.app.popupcalculator", "com.miui.calculator",
                        "com.oneplus.calculator", "com.coloros.calculator"};
            case "NotesIcon":
                return new String[]{"com.google.android.keep", "com.samsung.android.app.notes",
                        "com.miui.notes", "com.coloros.note", "com.nearme.note"};
            case "WeatherIcon":
                return new String[]{"com.google.android.apps.weather", "com.sec.android.daemonapp",
                        "com.miui.weather2", "com.coloros.weather2", "com.oneplus.weather"};
            case "ClockIcon":
                return new String[]{"com.google.android.deskclock", "com.android.deskclock",
                        "com.sec.android.app.clockpackage", "com.coloros.alarmclock", "com.oneplus.deskclock"};
            case "SettingsIcon":
                return new String[]{"com.android.settings"};
            default:
                return new String[0];
        }
    }

    // Первый реально установленный пакет-аналог маски (или null).
    private static String installedPackageFor(Context ctx, LauncherIcon mask) {
        PackageManager pm = ctx.getPackageManager();
        for (String pkg : systemPackagesFor(mask)) {
            try {
                pm.getApplicationInfo(pkg, 0);
                return pkg;
            } catch (PackageManager.NameNotFoundException ignore) {
            }
        }
        return null;
    }

    // Реальная иконка приложения-аналога с телефона (или null, если такого приложения нет —
    // тогда в меню показываем нашу заготовку).
    public static Drawable realIcon(Context ctx, LauncherIcon mask) {
        String pkg = installedPackageFor(ctx, mask);
        if (pkg == null) return null;
        try {
            return ctx.getPackageManager().getApplicationIcon(pkg);
        } catch (Exception e) {
            return null;
        }
    }

    // Реальное имя приложения-аналога с телефона (или наш дефолтный заголовок маски).
    public static CharSequence realLabel(Context ctx, LauncherIcon mask) {
        String pkg = installedPackageFor(ctx, mask);
        if (pkg != null) {
            try {
                return ctx.getPackageManager().getApplicationLabel(
                        ctx.getPackageManager().getApplicationInfo(pkg, 0));
            } catch (Exception ignore) {
            }
        }
        return LocaleController.getString(mask.title);
    }
}

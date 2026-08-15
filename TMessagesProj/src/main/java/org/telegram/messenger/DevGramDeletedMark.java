package org.telegram.messenger;

import android.graphics.drawable.Drawable;
import android.text.SpannableStringBuilder;

import androidx.core.content.ContextCompat;

import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.ColoredImageSpan;

/**
 * DevGram: кастомизация пометки удалённого сообщения (порт логики AyuGram, GPL).
 *
 * Пометка рисуется в строке времени сообщения (ChatMessageCell). Пользователь выбирает значок
 * (нет / корзина / крест / перечёркнутый глаз) и цвет (тема или из палитры). Раньше это был
 * хардкод-эмодзи «🗑»; теперь — тонируемый ColoredImageSpan.
 *
 * Ключи: dg_deletedIcon (0..3, деф 1=корзина), dg_deletedIconColor (0=тема, 1..7 — палитра).
 */
public final class DevGramDeletedMark {

    private DevGramDeletedMark() { }

    // значки: индекс 0 — «нет», далее корзина/крест/глаз
    public static final int[] ICONS = {
            0,
            R.drawable.devgram_deleted_trash,
            R.drawable.devgram_deleted_cross,
            R.drawable.devgram_deleted_eye,
    };

    // палитра цветов пометки (0 = цвет темы key_chat_inTimeText), остальные — как у AyuGram
    public static final int[] COLORS = {0xFFE44337, 0xFFDB2E37, 0xFFC03A53, 0xFF937A46, 0xFF4D849D, 0xFF474F2B};

    // размер значка под строку времени (как у AyuGram — компактный, не 18dp)
    private static final int ICON_SIZE = AndroidUtilities.dp(14);

    private static SpannableStringBuilder cached;
    private static int cachedIcon = -1;
    private static int cachedColor = -1;

    public static int getIcon() {
        int v = MessagesController.getGlobalMainSettings().getInt("dg_deletedIcon", 1);
        return (v < 0 || v >= ICONS.length) ? 1 : v;
    }

    public static void setIcon(int v) {
        MessagesController.getGlobalMainSettings().edit().putInt("dg_deletedIcon", v).apply();
        invalidate();
    }

    // 0 — цвет темы; 1..COLORS.length — палитра.
    public static int getColorIndex() {
        int v = MessagesController.getGlobalMainSettings().getInt("dg_deletedIconColor", 0);
        return (v < 0 || v > COLORS.length) ? 0 : v;
    }

    public static void setColorIndex(int v) {
        MessagesController.getGlobalMainSettings().edit().putInt("dg_deletedIconColor", v).apply();
        invalidate();
    }

    public static void invalidate() {
        cached = null;
        cachedIcon = -1;
        cachedColor = -1;
    }

    /** CharSequence-спан для вставки в строку времени (или пустой, если значок «нет»). */
    public static CharSequence getMark(Theme.ResourcesProvider resourcesProvider) {
        int icon = getIcon();
        if (icon == 0) return "";
        int colorIndex = getColorIndex();
        if (cached != null && cachedIcon == icon && cachedColor == colorIndex) return cached;

        Drawable drawable = ContextCompat.getDrawable(ApplicationLoader.applicationContext, ICONS[icon]);
        if (drawable == null) return "";
        drawable = drawable.mutate();

        SpannableStringBuilder sb = new SpannableStringBuilder("​");
        ColoredImageSpan span = new ColoredImageSpan(drawable);
        span.setSize(ICON_SIZE); // компактно под строку времени (как у AyuGram), а не 18dp
        int color = colorIndex > 0
                ? COLORS[colorIndex - 1]
                : Theme.getColor(Theme.key_chat_inTimeText, resourcesProvider);
        span.setOverrideColor(color);
        sb.setSpan(span, 0, 1, SpannableStringBuilder.SPAN_EXCLUSIVE_EXCLUSIVE);

        cached = sb;
        cachedIcon = icon;
        cachedColor = colorIndex;
        return sb;
    }

    /** Ширина значка + пробела в пикселях (Paint.measureText спаны не учитывает — добавляем вручную). */
    public static int getMarkWidth() {
        if (getIcon() == 0) return 0;
        return ICON_SIZE + AndroidUtilities.dp(3); // значок (setSize) + пробел до времени
    }
}

package org.telegram.ui.Components;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.text.TextPaint;
import android.view.View;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.DevGramConfig;
import org.telegram.messenger.LocaleController;
import org.telegram.ui.ActionBar.Theme;

// DevGram: превью вкладок папок (как FoldersPreviewCell в exteraGram) — три вкладки-пилюли
// с учётом стиля заголовков (Текст / Значок+текст / Значок).
public class DevGramFoldersPreviewCell extends View {

    private final Paint pill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint dot = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final TextPaint text = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();

    private static final String[] TABS = {"Все чаты", "Работа", "Личное"};
    private static final int[] COUNTS = {34, 17, 3};

    public DevGramFoldersPreviewCell(Context context) {
        super(context);
        setWillNotDraw(false);
        setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
        text.setTextSize(AndroidUtilities.dp(14));
        text.setTypeface(AndroidUtilities.bold());
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(60), MeasureSpec.EXACTLY));
    }

    @Override
    protected void onDraw(Canvas canvas) {
        int accent = Theme.getColor(Theme.key_switchTrack);
        int r = Color.red(accent), g = Color.green(accent), b = Color.blue(accent);
        int style = DevGramConfig.getFolderTabsStyle(); // 0 текст, 1 значок+текст, 2 значок
        float h = getMeasuredHeight();
        float cy = h / 2f;
        float x = AndroidUtilities.dp(16);
        boolean rtl = LocaleController.isRTL;
        if (rtl) x = getMeasuredWidth() - x;

        for (int i = 0; i < TABS.length; i++) {
            boolean sel = i == 0;
            String label = TABS[i];
            float iconW = (style == 1 || style == 2) ? AndroidUtilities.dp(18) : 0;
            float textW = (style == 2) ? 0 : text.measureText(label);
            float gap = (style == 1 && textW > 0) ? AndroidUtilities.dp(6) : 0;
            float padH = AndroidUtilities.dp(14);
            float pillW = padH * 2 + iconW + gap + textW;
            float left = rtl ? x - pillW : x;
            float right = left + pillW;

            pill.setColor(sel ? Color.argb(255, r, g, b) : Color.argb(28, r, g, b));
            rect.set(left, cy - AndroidUtilities.dp(16), right, cy + AndroidUtilities.dp(16));
            canvas.drawRoundRect(rect, AndroidUtilities.dp(16), AndroidUtilities.dp(16), pill);

            float cx = left + padH;
            int fg = sel ? Color.WHITE : Color.argb(200, r, g, b);
            if (iconW > 0) {
                dot.setColor(fg);
                rect.set(cx, cy - iconW / 2f, cx + iconW, cy + iconW / 2f);
                canvas.drawRoundRect(rect, AndroidUtilities.dp(4), AndroidUtilities.dp(4), dot);
                cx += iconW + gap;
            }
            if (textW > 0) {
                text.setColor(fg);
                canvas.drawText(label, cx, cy + AndroidUtilities.dp(5), text);
            }
            x = rtl ? left - AndroidUtilities.dp(8) : right + AndroidUtilities.dp(8);
        }
    }
}

/*
 * DevGram: значок стрика — огонёк 🔥 с числом ВНУТРИ (сколько дней подряд общаетесь).
 * Пламя рисуем системным эмодзи (всегда аккуратно), число — белым по центру тела пламени.
 * Используется как right-drawable рядом с именем (справа от прем-эмодзи, где был значок).
 */

package org.telegram.ui.Components;

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.text.TextPaint;

import androidx.annotation.NonNull;

import org.telegram.messenger.AndroidUtilities;

public class DevGramStreakDrawable extends Drawable {

    private final TextPaint flamePaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final TextPaint numPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final int size;
    private String numStr = "";

    public DevGramStreakDrawable() {
        size = AndroidUtilities.dp(22);
        flamePaint.setTextSize(AndroidUtilities.dp(22));
        numPaint.setColor(0xFFFFFFFF);
        numPaint.setTypeface(AndroidUtilities.bold());
        numPaint.setTextAlign(Paint.Align.CENTER);
        numPaint.setShadowLayer(AndroidUtilities.dp(1), 0, 0, 0x66000000);
    }

    public void setCount(int count) {
        String s = String.valueOf(Math.max(0, count));
        if (s.equals(numStr)) {
            return;
        }
        numStr = s;
        // подгоняем размер числа под количество цифр, чтобы влезало в пламя
        numPaint.setTextSize(AndroidUtilities.dp(numStr.length() >= 3 ? 7f : (numStr.length() == 2 ? 8f : 9.5f)));
        invalidateSelf();
    }

    @Override
    public void draw(@NonNull Canvas canvas) {
        Rect b = getBounds();
        if (b.isEmpty() || numStr.isEmpty()) {
            return;
        }
        float cx = b.exactCenterX();
        Paint.FontMetrics fm = flamePaint.getFontMetrics();
        float flameBaseline = b.top + (b.height() - (fm.descent - fm.ascent)) / 2f - fm.ascent;
        canvas.drawText("🔥", cx, flameBaseline, flamePaint); // 🔥
        // число — в теле пламени (нижняя часть по центру)
        float ny = b.top + b.height() * 0.66f - (numPaint.descent() + numPaint.ascent()) / 2f;
        canvas.drawText(numStr, cx, ny, numPaint);
    }

    @Override
    public int getIntrinsicWidth() {
        return size;
    }

    @Override
    public int getIntrinsicHeight() {
        return size;
    }

    @Override
    public void setAlpha(int alpha) {
        flamePaint.setAlpha(alpha);
        numPaint.setAlpha(alpha);
    }

    @Override
    public void setColorFilter(ColorFilter colorFilter) {
    }

    @Override
    public int getOpacity() {
        return PixelFormat.TRANSLUCENT;
    }
}

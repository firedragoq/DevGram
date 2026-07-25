/*
 * DevGram: значок стрика — огонёк 🔥 с числом ВНУТРИ (сколько дней подряд общаетесь).
 * Пламя — системный эмодзи (по центру), число — белым по центру тела пламени. Пламя живо
 * «колышется» (лёгкое мерцание по вертикали), число при этом стоит на месте.
 */

package org.telegram.ui.Components;

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.os.SystemClock;
import android.text.TextPaint;

import androidx.annotation.NonNull;

import org.telegram.messenger.AndroidUtilities;

public class DevGramStreakDrawable extends Drawable {

    private final TextPaint flamePaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final TextPaint numPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final int size;
    private String numStr = "";

    private final Runnable invalidateRunnable = this::invalidateSelf;

    public DevGramStreakDrawable() {
        size = AndroidUtilities.dp(18);
        flamePaint.setTextSize(AndroidUtilities.dp(18));
        flamePaint.setTextAlign(Paint.Align.CENTER); // пламя строго по центру
        numPaint.setColor(0xFFFFFFFF);
        numPaint.setTypeface(AndroidUtilities.bold());
        numPaint.setTextAlign(Paint.Align.CENTER);
        numPaint.setShadowLayer(AndroidUtilities.dp(1.2f), 0, 0, 0x99000000);
    }

    public void setCount(int count) {
        String s = String.valueOf(Math.max(0, count));
        if (s.equals(numStr)) {
            return;
        }
        numStr = s;
        // размер числа под количество цифр, чтобы влезало в тело пламени
        numPaint.setTextSize(AndroidUtilities.dp(numStr.length() >= 3 ? 6.5f : (numStr.length() == 2 ? 7.5f : 8.5f)));
        invalidateSelf();
    }

    @Override
    public void draw(@NonNull Canvas canvas) {
        Rect b = getBounds();
        if (b.isEmpty() || numStr.isEmpty()) {
            return;
        }
        float cx = b.exactCenterX();
        // мерцание: пламя слегка тянется вверх/сжимается от своего основания
        float phase = (SystemClock.uptimeMillis() % 780L) / 780f;
        float sy = 1f + 0.09f * (float) Math.sin(phase * 2 * Math.PI);

        Paint.FontMetrics fm = flamePaint.getFontMetrics();
        float flameBaseline = b.top + (b.height() - (fm.descent - fm.ascent)) / 2f - fm.ascent;
        canvas.save();
        canvas.scale(1f, sy, cx, b.bottom); // тянем от основания
        canvas.drawText("🔥", cx, flameBaseline, flamePaint); // 🔥
        canvas.restore();

        // число — в теле пламени (нижняя середина), стоит на месте
        float ny = b.top + b.height() * 0.62f - (numPaint.descent() + numPaint.ascent()) / 2f;
        canvas.drawText(numStr, cx, ny, numPaint);

        // следующий кадр анимации (работает там, где у drawable есть callback-вью)
        unscheduleSelf(invalidateRunnable);
        scheduleSelf(invalidateRunnable, SystemClock.uptimeMillis() + 40);
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

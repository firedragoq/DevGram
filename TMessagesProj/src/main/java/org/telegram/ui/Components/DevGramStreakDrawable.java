/*
 * DevGram: значок стрика — АНИМИРОВАННЫЙ кастом-эмодзи «огонь» (document_id ниже) с числом
 * ВНУТРИ. Число рисуем белым с чёрным контуром, чтобы читалось на любом кадре пламени.
 * Требует жизненного цикла attach()/detach() (как эмодзи-статус) — вызывать из контейнера.
 */

package org.telegram.ui.Components;

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.text.TextPaint;
import android.view.View;

import androidx.annotation.NonNull;

import org.telegram.messenger.AndroidUtilities;

public class DevGramStreakDrawable extends Drawable {

    // Кастом-эмодзи «огонь» (анимированный), заданный пользователем.
    public static final long FLAME_EMOJI = 5415735648931848880L;

    private final AnimatedEmojiDrawable.SwapAnimatedEmojiDrawable emoji;
    private final TextPaint numPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final TextPaint numStroke = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final int size;
    private String numStr = "";

    public DevGramStreakDrawable(View parentView, int account) {
        size = AndroidUtilities.dp(18);
        emoji = new AnimatedEmojiDrawable.SwapAnimatedEmojiDrawable(parentView, size, AnimatedEmojiDrawable.CACHE_TYPE_EMOJI_STATUS);
        emoji.setCurrentAccount(account);
        emoji.set(FLAME_EMOJI, false);

        numPaint.setColor(0xFFFFFFFF);
        numPaint.setTypeface(AndroidUtilities.bold());
        numPaint.setTextAlign(Paint.Align.CENTER);

        numStroke.setColor(0xFF000000);
        numStroke.setTypeface(AndroidUtilities.bold());
        numStroke.setTextAlign(Paint.Align.CENTER);
        numStroke.setStyle(Paint.Style.STROKE);
        numStroke.setStrokeWidth(AndroidUtilities.dp(1.5f));
        numStroke.setStrokeJoin(Paint.Join.ROUND);
    }

    public void attach() {
        emoji.attach();
    }

    public void detach() {
        emoji.detach();
    }

    public void setCount(int count) {
        String s = String.valueOf(Math.max(0, count));
        if (s.equals(numStr)) {
            return;
        }
        numStr = s;
        float ts = AndroidUtilities.dp(numStr.length() >= 3 ? 6.5f : (numStr.length() == 2 ? 7.5f : 8.5f));
        numPaint.setTextSize(ts);
        numStroke.setTextSize(ts);
        invalidateSelf();
    }

    @Override
    public void draw(@NonNull Canvas canvas) {
        Rect b = getBounds();
        if (b.isEmpty() || numStr.isEmpty()) {
            return;
        }
        emoji.setBounds(b);
        emoji.draw(canvas); // анимированное пламя
        float cx = b.exactCenterX();
        // число — по центру тела пламени (нижняя середина); белое с чёрным контуром
        float ny = b.top + b.height() * 0.62f - (numPaint.descent() + numPaint.ascent()) / 2f;
        canvas.drawText(numStr, cx, ny, numStroke);
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
    }

    @Override
    public void setColorFilter(ColorFilter colorFilter) {
    }

    @Override
    public int getOpacity() {
        return PixelFormat.TRANSLUCENT;
    }
}

package org.telegram.ui.pillstack.pills;

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.ui.ActionBar.Theme;

/** DevGram: порт ColoredBackground из exteraGram — вертикальный градиент со скруглением 14dp + блик в тёмной теме. */
public class ColoredBackground extends Drawable {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint strokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    public ColoredBackground() {
        this(0xFF13355D, 0xFF1487E1);
    }

    public ColoredBackground(int top, int bottom) {
        Shader.TileMode clamp = Shader.TileMode.CLAMP;
        paint.setShader(new LinearGradient(0, 0, 0, AndroidUtilities.dp(28), new int[]{top, bottom}, new float[]{0f, 1f}, clamp));
        strokePaint.setStyle(Paint.Style.STROKE);
        strokePaint.setStrokeWidth(AndroidUtilities.dp(1));
        strokePaint.setShader(new LinearGradient(0, 0, 0, AndroidUtilities.dp(28), new int[]{0x4DFFFFFF, 0, 0x1BFFFFFF}, new float[]{0f, 0.5f, 1f}, clamp));
    }

    @Override
    public void draw(Canvas canvas) {
        Rect bounds = getBounds();
        float r = AndroidUtilities.dp(14);
        RectF rectF = AndroidUtilities.rectTmp;
        rectF.set(bounds);
        canvas.drawRoundRect(rectF, r, r, paint);
        if (!Theme.isCurrentThemeDark()) return;
        float sw = AndroidUtilities.dp(1);
        strokePaint.setStrokeWidth(sw);
        float half = sw / 2f;
        rectF.inset(half, half);
        canvas.drawRoundRect(rectF, r, r, strokePaint);
    }

    @Override public int getOpacity() { return PixelFormat_TRANSLUCENT; }
    private static final int PixelFormat_TRANSLUCENT = -3;

    @Override public void setAlpha(int alpha) { paint.setAlpha(alpha); strokePaint.setAlpha(alpha); }
    @Override public void setColorFilter(ColorFilter cf) { paint.setColorFilter(cf); strokePaint.setColorFilter(cf); }
}

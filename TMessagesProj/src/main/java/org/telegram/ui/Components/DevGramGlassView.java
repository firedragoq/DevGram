/*
 * DevGram: «жидкое стекло» (Liquid Glass) для плагинов.
 *
 * Настоящий backdrop-blur (размывается то, что ПОЗАДИ панели, а не её собственный
 * контент), плюс стеклянная эстетика iOS: тонировка, глянцевый отблеск сверху и
 * светящаяся кромка. Работает на ВСЕХ версиях Android (софт-рендер + нативный
 * Utilities.stackBlurBitmap), в отличие от View.setRenderEffect (только API 31+).
 *
 * Техника BlurView: корневая вью рисуется в уменьшенный bitmap со сдвигом на позицию
 * панели → стек-блюр → рисуется обратно со скруглением. Рекурсия (панель рисует саму
 * себя внутри корня) гасится статичным флагом capturing.
 *
 * Это FrameLayout — в него можно класть контент, он ляжет чётко поверх стекла.
 * Из Python создаётся через DevGramPlugins.glassPanel(...) / glassBehind(...).
 */
package org.telegram.ui.Components;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.view.View;
import android.widget.FrameLayout;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.Utilities;

public class DevGramGlassView extends FrameLayout {

    // пока хоть одна панель захватывает фон — остальные не рисуют себя (гасим рекурсию/самозахват)
    private static boolean capturing;

    // корень, чей контент размывается (по умолчанию — окно целиком)
    private View blurRoot;

    // параметры стекла
    private float cornerRadius = AndroidUtilities.dp(22);
    private int blurRadius = AndroidUtilities.dp(18);   // «сила» размытия в dp
    private int tintColor = 0x26FFFFFF;                  // лёгкая тонировка (ARGB)
    private float borderLight = 0.6f;                    // яркость светящейся кромки 0..1
    private int downScale = 7;                           // во сколько раз ужимаем перед блюром
    private boolean live = true;                         // следить за фоном (перерисовки)
    private long frameIntervalMs = 32;                   // троттлинг перезахвата (~30 fps)

    // рабочие буферы
    private Bitmap smallBitmap;
    private Canvas smallCanvas;
    private final Matrix shaderMatrix = new Matrix();
    private BitmapShader bitmapShader;
    private long lastCapture;

    private final Paint backdropPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final Paint tintPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint sheenPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint rimPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rectF = new RectF();

    private final int[] locRoot = new int[2];
    private final int[] locSelf = new int[2];

    public DevGramGlassView(Context context) {
        super(context);
        setWillNotDraw(false);
        rimPaint.setStyle(Paint.Style.STROKE);
        tintPaint.setColor(tintColor);
    }

    // ---- настройка (вызывается из моста / плагина) ----
    public DevGramGlassView setCornerRadiusDp(int dp) { cornerRadius = AndroidUtilities.dp(dp); invalidate(); return this; }
    public DevGramGlassView setBlurStrengthDp(int dp) { blurRadius = AndroidUtilities.dp(dp); invalidate(); return this; }
    public DevGramGlassView setTint(int argb) { tintColor = argb; tintPaint.setColor(argb); invalidate(); return this; }
    public DevGramGlassView setBorderLight(float v) { borderLight = Math.max(0f, Math.min(1f, v)); invalidate(); return this; }
    public DevGramGlassView setLive(boolean v) { live = v; invalidate(); return this; }
    public DevGramGlassView setBlurRoot(View v) { blurRoot = v; return this; }

    private View resolveRoot() {
        return blurRoot != null ? blurRoot : getRootView();
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        if (smallBitmap != null) {
            try { smallBitmap.recycle(); } catch (Throwable ignore) {}
            smallBitmap = null;
            smallCanvas = null;
            bitmapShader = null;
        }
    }

    @Override
    protected void dispatchDraw(Canvas canvas) {
        // во время захвата фона панель себя не рисует — иначе снимет саму себя / уйдёт в рекурсию
        if (capturing) {
            return;
        }
        final int w = getWidth();
        final int h = getHeight();
        if (w <= 0 || h <= 0) {
            super.dispatchDraw(canvas);
            return;
        }

        boolean ok = false;
        try {
            ok = updateBackdrop(w, h);
        } catch (Throwable e) {
            FileLog.e(e);
        }

        rectF.set(0, 0, w, h);
        final float r = Math.min(cornerRadius, Math.min(w, h) / 2f);

        // 1) размытый фон, обрезанный по скруглённому прямоугольнику
        if (ok && bitmapShader != null) {
            shaderMatrix.reset();
            shaderMatrix.setScale((float) w / smallBitmap.getWidth(), (float) h / smallBitmap.getHeight());
            bitmapShader.setLocalMatrix(shaderMatrix);
            backdropPaint.setShader(bitmapShader);
            canvas.drawRoundRect(rectF, r, r, backdropPaint);
        }

        // 2) стеклянная тонировка
        if (Color.alpha(tintColor) != 0) {
            canvas.drawRoundRect(rectF, r, r, tintPaint);
        }

        // 3) глянцевый отблеск сверху (светлее вверху -> прозрачный к середине)
        if (borderLight > 0f) {
            int topA = (int) (70 * borderLight);
            sheenPaint.setShader(new LinearGradient(0, 0, 0, h,
                    Color.argb(topA, 255, 255, 255), Color.argb(0, 255, 255, 255), Shader.TileMode.CLAMP));
            canvas.drawRoundRect(rectF, r, r, sheenPaint);
            sheenPaint.setShader(null);
        }

        // 4) контент плагина — поверх стекла
        super.dispatchDraw(canvas);

        // 5) светящаяся кромка (яркая по верх-лево, гаснет к низ-право)
        if (borderLight > 0f) {
            float stroke = AndroidUtilities.dp(1.4f);
            rimPaint.setStrokeWidth(stroke);
            int a1 = (int) (180 * borderLight);
            rimPaint.setShader(new LinearGradient(0, 0, w, h,
                    new int[]{Color.argb(a1, 255, 255, 255), Color.argb((int) (20 * borderLight), 255, 255, 255), Color.argb((int) (90 * borderLight), 255, 255, 255)},
                    new float[]{0f, 0.5f, 1f}, Shader.TileMode.CLAMP));
            float inset = stroke / 2f;
            rectF.set(inset, inset, w - inset, h - inset);
            canvas.drawRoundRect(rectF, r, r, rimPaint);
            rimPaint.setShader(null);
        }

        if (live && !capturing) {
            postInvalidateDelayed(frameIntervalMs);
        }
    }

    // Захватить фон под панелью и размыть. true — буфер готов.
    private boolean updateBackdrop(int w, int h) {
        long now = System.currentTimeMillis();
        boolean sizeChanged = smallBitmap == null
                || smallBitmap.getWidth() != Math.max(1, w / downScale)
                || smallBitmap.getHeight() != Math.max(1, h / downScale);
        if (!sizeChanged && (now - lastCapture) < frameIntervalMs && bitmapShader != null) {
            return true; // используем прошлый кадр — троттлинг
        }

        final int sw = Math.max(1, w / downScale);
        final int sh = Math.max(1, h / downScale);
        if (sizeChanged) {
            if (smallBitmap != null) {
                try { smallBitmap.recycle(); } catch (Throwable ignore) {}
            }
            smallBitmap = Bitmap.createBitmap(sw, sh, Bitmap.Config.ARGB_8888);
            smallCanvas = new Canvas(smallBitmap);
            bitmapShader = new BitmapShader(smallBitmap, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP);
        } else {
            smallBitmap.eraseColor(Color.TRANSPARENT);
        }

        final View root = resolveRoot();
        if (root == null) {
            return false;
        }
        root.getLocationInWindow(locRoot);
        getLocationInWindow(locSelf);
        float dx = locSelf[0] - locRoot[0];
        float dy = locSelf[1] - locRoot[1];

        smallCanvas.save();
        smallCanvas.scale(1f / downScale, 1f / downScale);
        smallCanvas.translate(-dx, -dy);
        capturing = true;
        try {
            root.draw(smallCanvas);
        } catch (Throwable e) {
            FileLog.e(e);
        } finally {
            capturing = false;
            smallCanvas.restore();
        }

        int rad = Math.max(2, Math.min(60, blurRadius / downScale + 4));
        try {
            Utilities.stackBlurBitmap(smallBitmap, rad);
        } catch (Throwable e) {
            FileLog.e(e);
        }
        lastCapture = now;
        return true;
    }
}

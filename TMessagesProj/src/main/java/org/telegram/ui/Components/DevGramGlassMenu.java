package org.telegram.ui.Components;

import android.graphics.Bitmap;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;
import android.view.View;

import androidx.annotation.NonNull;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.Utilities;

// DevGram (как exteraGram, GlassMessageMenu): «матовое стекло» для меню сообщения.
// Меню — отдельное окно, живой блюр под ним недоступен, поэтому делаем СНИМОК контента чата,
// размываем его (Utilities.stackBlurBitmap) и рисуем как фон меню, выравнивая по позиции меню
// на экране (через getLocationOnScreen). Сверху — полупрозрачная подложка цвета меню.
public class DevGramGlassMenu {

    private static final int DOWNSCALE = 8;   // во сколько раз ужимаем снимок перед блюром
    private static final int BLUR_RADIUS = 8; // радиус stackBlur в пикселях уменьшенного снимка

    // Снять и размыть контент. Вернёт null, если вью ещё не измерена.
    public static Bitmap snapshot(View root) {
        if (root == null || root.getWidth() <= 0 || root.getHeight() <= 0) {
            return null;
        }
        try {
            int w = Math.max(1, root.getWidth() / DOWNSCALE);
            int h = Math.max(1, root.getHeight() / DOWNSCALE);
            Bitmap bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
            Canvas c = new Canvas(bmp);
            c.scale(1f / DOWNSCALE, 1f / DOWNSCALE);
            root.draw(c);
            Utilities.stackBlurBitmap(bmp, BLUR_RADIUS);
            return bmp;
        } catch (Throwable e) {
            return null;
        }
    }

    // Фон-drawable «стекло»: рисует размытый снимок, выровненный под попапом, + подложку-тинт.
    public static class GlassDrawable extends Drawable {
        private final Bitmap bitmap;
        private final BitmapShader shader;
        private final Paint blurPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint tintPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Matrix matrix = new Matrix();
        private final RectF rectF = new RectF();
        private final float radius;
        private final Rect padding;
        private final View hostView;       // сам попап (его позиция на экране)
        private final View contentView;    // контент чата (снимок сделан с него)
        private final int[] h = new int[2], cc = new int[2];

        public GlassDrawable(Bitmap bitmap, View hostView, View contentView, int tintColor, float radius, Rect padding) {
            this.bitmap = bitmap;
            this.hostView = hostView;
            this.contentView = contentView;
            this.radius = radius;
            this.padding = padding;
            shader = new BitmapShader(bitmap, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP);
            blurPaint.setShader(shader);
            // подложка: цвет меню с пониженной непрозрачностью, чтобы блюр просвечивал
            tintPaint.setColor(Color.argb(150, Color.red(tintColor), Color.green(tintColor), Color.blue(tintColor)));
        }

        @Override
        public void draw(@NonNull Canvas canvas) {
            Rect b = getBounds();
            if (b.width() <= 0 || b.height() <= 0 || bitmap == null || bitmap.isRecycled()) {
                return;
            }
            hostView.getLocationOnScreen(h);
            contentView.getLocationOnScreen(cc);
            // bitmap уменьшен в DOWNSCALE раз; на экране пиксель снимка = contentLoc + bmp*DOWNSCALE.
            // локальные координаты канвы = экран - hostLoc. Значит матрица bmp->local:
            matrix.reset();
            matrix.setScale(DOWNSCALE, DOWNSCALE);
            matrix.postTranslate(cc[0] - h[0], cc[1] - h[1]);
            shader.setLocalMatrix(matrix);
            rectF.set(b.left, b.top, b.right, b.bottom);
            canvas.drawRoundRect(rectF, radius, radius, blurPaint);
            canvas.drawRoundRect(rectF, radius, radius, tintPaint);
        }

        @Override
        public boolean getPadding(@NonNull Rect p) {
            if (padding != null) {
                p.set(padding.left, padding.top, padding.right, padding.bottom);
                return true;
            }
            return super.getPadding(p);
        }

        @Override
        public void setAlpha(int alpha) {
            blurPaint.setAlpha(alpha);
        }

        @Override
        public void setColorFilter(ColorFilter colorFilter) {
            // намеренно игнорируем тонирование (ActionBarPopupWindowLayout.setBackgroundColor),
            // иначе стекло перекрасится в сплошной цвет
        }

        @Override
        public int getOpacity() {
            return PixelFormat.TRANSLUCENT;
        }
    }

    public static float defaultRadius() {
        return AndroidUtilities.dp(10);
    }

    // Переиспользуемый «стеклянный» рисовальщик для вью, которые рисуют фон сами (напр. панель
    // реакций). Держит один снимок, выравнивает его по позиции host на экране.
    public static class GlassPainter {
        private Bitmap bitmap;
        private BitmapShader shader;
        private final Paint blurPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint tintPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Matrix matrix = new Matrix();
        private final int[] h = new int[2], cc = new int[2];
        private final int tintColor;

        public GlassPainter(int tintColor) {
            this.tintColor = tintColor;
            tintPaint.setColor(Color.argb(150, Color.red(tintColor), Color.green(tintColor), Color.blue(tintColor)));
        }

        public boolean ready() {
            return bitmap != null && !bitmap.isRecycled();
        }

        // Снять контент один раз (ленивая инициализация).
        public void ensure(View contentRoot) {
            if (bitmap == null && contentRoot != null) {
                bitmap = snapshot(contentRoot);
                if (bitmap != null) {
                    shader = new BitmapShader(bitmap, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP);
                    blurPaint.setShader(shader);
                }
            }
        }

        // Нарисовать стекло в скруглённый rect (в локальных координатах host).
        public void draw(Canvas canvas, View host, View contentRoot, RectF rect, float radius, int alpha) {
            if (!ready() || host == null || contentRoot == null) {
                return;
            }
            host.getLocationOnScreen(h);
            contentRoot.getLocationOnScreen(cc);
            matrix.reset();
            matrix.setScale(DOWNSCALE, DOWNSCALE);
            matrix.postTranslate(cc[0] - h[0], cc[1] - h[1]);
            shader.setLocalMatrix(matrix);
            blurPaint.setAlpha(alpha);
            tintPaint.setAlpha(alpha * 150 / 255);
            canvas.drawRoundRect(rect, radius, radius, blurPaint);
            canvas.drawRoundRect(rect, radius, radius, tintPaint);
        }
    }
}

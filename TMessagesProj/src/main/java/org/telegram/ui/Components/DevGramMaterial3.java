package org.telegram.ui.Components;

import android.content.Context;
import android.graphics.Canvas;
import android.view.ContextThemeWrapper;
import android.view.View;

import com.google.android.material.progressindicator.CircularProgressIndicatorSpec;
import com.google.android.material.progressindicator.IndeterminateDrawable;

import org.telegram.messenger.MessagesController;

// DevGram: настоящие Material 3 виджеты (как exteraGram getNew*Style). Пока — индикатор загрузки:
// хостим Material IndeterminateDrawable внутри существующих вью, без подмены самих вью по всему коду.
public class DevGramMaterial3 {

    public static boolean loadingEnabled() {
        return MessagesController.getGlobalMainSettings().getBoolean("dg_md3_progress", false);
    }

    // Материаловым виджетам нужна тема Material3 в контексте, иначе не заинфлейтятся/упадут.
    public static Context themed(Context base) {
        return new ContextThemeWrapper(base, com.google.android.material.R.style.Theme_Material3_DayNight);
    }

    // Обёртка над круговым M3-индикатором: пересоздаёт drawable при смене размера/цвета, сам анимируется.
    public static class CircularHost {
        private IndeterminateDrawable<CircularProgressIndicatorSpec> drawable;
        private int lastSize = -1, lastColor = 0, lastThickness = -1;
        private final View host;

        public CircularHost(View host) {
            this.host = host;
        }

        private void ensure(int sizePx, int thicknessPx, int color) {
            if (drawable != null && sizePx == lastSize && color == lastColor && thicknessPx == lastThickness) {
                return;
            }
            lastSize = sizePx;
            lastColor = color;
            lastThickness = thicknessPx;
            Context ctx = themed(host.getContext());
            CircularProgressIndicatorSpec spec = new CircularProgressIndicatorSpec(ctx, null);
            spec.indicatorSize = sizePx;
            spec.trackThickness = thicknessPx;
            spec.indicatorInset = 0;
            spec.indicatorColors = new int[]{color};
            spec.trackColor = (color & 0x00FFFFFF) | 0x22000000;
            drawable = IndeterminateDrawable.createCircularDrawable(ctx, spec);
            drawable.setVisible(true, false);
            drawable.setCallback(new android.graphics.drawable.Drawable.Callback() {
                @Override public void invalidateDrawable(android.graphics.drawable.Drawable who) { host.invalidate(); }
                @Override public void scheduleDrawable(android.graphics.drawable.Drawable who, Runnable what, long when) {
                    host.postDelayed(what, when - android.os.SystemClock.uptimeMillis());
                }
                @Override public void unscheduleDrawable(android.graphics.drawable.Drawable who, Runnable what) {
                    host.removeCallbacks(what);
                }
            });
        }

        // Нарисовать индикатор с центром (cx,cy). Возвращает false, если не удалось (тогда рисуй по-старому).
        public boolean draw(Canvas canvas, float cx, float cy, int sizePx, int thicknessPx, int color, int alpha) {
            try {
                ensure(sizePx, thicknessPx, color);
                drawable.setAlpha(alpha);
                int half = sizePx / 2;
                drawable.setBounds((int) (cx - half), (int) (cy - half), (int) (cx + half), (int) (cy + half));
                drawable.draw(canvas);
                return true;
            } catch (Throwable e) {
                return false;
            }
        }
    }
}

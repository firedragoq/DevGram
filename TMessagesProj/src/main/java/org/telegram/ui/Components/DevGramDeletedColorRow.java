package org.telegram.ui.Components;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.view.MotionEvent;
import android.view.View;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.DevGramDeletedMark;
import org.telegram.messenger.Utilities;
import org.telegram.ui.ActionBar.Theme;

/**
 * DevGram: инлайн-палитра цвета пометки удалённого (как у AyuGram, вместо диалога-меню).
 * Ряд кружков: 0 — «цвет темы» (кольцо), далее палитра. Выбранный обведён кольцом.
 */
@SuppressLint("ViewConstructor")
public class DevGramDeletedColorRow extends View {

    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint ring = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final int count;
    private int selected;
    private Utilities.Callback<Integer> onColorClick;
    private final Theme.ResourcesProvider resourcesProvider;

    public DevGramDeletedColorRow(Context context, Theme.ResourcesProvider rp) {
        super(context);
        this.resourcesProvider = rp;
        this.count = DevGramDeletedMark.COLORS.length + 1; // тема + палитра
        this.selected = DevGramDeletedMark.getColorIndex();
        ring.setStyle(Paint.Style.STROKE);
        ring.setStrokeWidth(AndroidUtilities.dp(2));
    }

    public void setOnColorClick(Utilities.Callback<Integer> cb) { this.onColorClick = cb; }

    public void setSelected(int index) { selected = index; invalidate(); }

    private int colorAt(int i) {
        if (i == 0) return Theme.getColor(Theme.key_chat_inTimeText, resourcesProvider);
        return DevGramDeletedMark.COLORS[i - 1];
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        setMeasuredDimension(MeasureSpec.getSize(widthMeasureSpec), AndroidUtilities.dp(56));
    }

    @Override
    protected void onDraw(Canvas canvas) {
        int pad = AndroidUtilities.dp(18);
        int r = AndroidUtilities.dp(15);
        int cy = getMeasuredHeight() / 2;
        float step = (getMeasuredWidth() - pad * 2f) / (count - 1);
        for (int i = 0; i < count; i++) {
            float cx = pad + step * i;
            int color = colorAt(i);
            if (i == 0) {
                // «тема» — кольцо без заливки
                ring.setColor(color);
                canvas.drawCircle(cx, cy, r - AndroidUtilities.dp(1), ring);
            } else {
                fill.setColor(color);
                canvas.drawCircle(cx, cy, r, fill);
            }
            if (i == selected) {
                ring.setColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlueText, resourcesProvider));
                canvas.drawCircle(cx, cy, r + AndroidUtilities.dp(4), ring);
            }
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (event.getAction() == MotionEvent.ACTION_UP) {
            int pad = AndroidUtilities.dp(18);
            float step = (getMeasuredWidth() - pad * 2f) / (count - 1);
            float x = event.getX();
            int best = 0;
            float bestD = Float.MAX_VALUE;
            for (int i = 0; i < count; i++) {
                float cx = pad + step * i;
                float d = Math.abs(x - cx);
                if (d < bestD) { bestD = d; best = i; }
            }
            if (best != selected) {
                setSelected(best);
                if (onColorClick != null) onColorClick.run(best);
            }
            performHapticFeedback(3);
            return true;
        }
        return true;
    }
}

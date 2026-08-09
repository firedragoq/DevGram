package org.telegram.ui.Components;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.View;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.DevGramConfig;
import org.telegram.ui.ActionBar.Theme;

// DevGram: превью плавающей кнопки (как FabShapeCell в exteraGram) — две карточки:
// круглая и квадратная (squircle); выбранная подсвечена рамкой. Тап переключает форму.
public class DevGramFabPreviewCell extends View {

    public interface OnPick { void pick(boolean square); }

    private final Paint card = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint outline = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint fab = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint plus = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();
    private OnPick onPick;

    public DevGramFabPreviewCell(Context context) {
        super(context);
        setWillNotDraw(false);
        setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
        outline.setStyle(Paint.Style.STROKE);
        outline.setStrokeWidth(AndroidUtilities.dp(2));
        plus.setStyle(Paint.Style.STROKE);
        plus.setStrokeWidth(AndroidUtilities.dp(2.5f));
        plus.setColor(Color.WHITE);
    }

    public void setOnPick(OnPick p) { onPick = p; }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(96), MeasureSpec.EXACTLY));
    }

    private RectF cardRect(int i) {
        float w = getMeasuredWidth();
        float pad = AndroidUtilities.dp(16), gap = AndroidUtilities.dp(12);
        float cw = (w - pad * 2 - gap) / 2f;
        float left = pad + i * (cw + gap);
        return new RectF(left, AndroidUtilities.dp(10), left + cw, getMeasuredHeight() - AndroidUtilities.dp(10));
    }

    @Override
    protected void onDraw(Canvas canvas) {
        int accent = Theme.getColor(Theme.key_switchTrack);
        boolean square = DevGramConfig.squareFab;
        for (int i = 0; i < 2; i++) {
            boolean isSquareCard = i == 1;
            RectF cr = cardRect(i);
            card.setColor(Color.argb(20, Color.red(accent), Color.green(accent), Color.blue(accent)));
            canvas.drawRoundRect(cr, AndroidUtilities.dp(12), AndroidUtilities.dp(12), card);
            boolean sel = isSquareCard == square;
            if (sel) {
                outline.setColor(accent);
                rect.set(cr.left + AndroidUtilities.dp(1), cr.top + AndroidUtilities.dp(1),
                        cr.right - AndroidUtilities.dp(1), cr.bottom - AndroidUtilities.dp(1));
                canvas.drawRoundRect(rect, AndroidUtilities.dp(12), AndroidUtilities.dp(12), outline);
            }
            // сам FAB
            float fs = AndroidUtilities.dp(40);
            float fcx = cr.centerX(), fcy = cr.centerY();
            fab.setColor(Theme.getColor(Theme.key_chats_actionBackground));
            rect.set(fcx - fs / 2, fcy - fs / 2, fcx + fs / 2, fcy + fs / 2);
            float rad = isSquareCard ? AndroidUtilities.dp(13) : fs / 2f;
            canvas.drawRoundRect(rect, rad, rad, fab);
            float pl = AndroidUtilities.dp(9);
            canvas.drawLine(fcx - pl, fcy, fcx + pl, fcy, plus);
            canvas.drawLine(fcx, fcy - pl, fcx, fcy + pl, plus);
        }
    }

    @Override
    public boolean onTouchEvent(android.view.MotionEvent e) {
        if (e.getAction() == android.view.MotionEvent.ACTION_UP && onPick != null) {
            float x = e.getX();
            boolean square = cardRect(1).contains(x, e.getY());
            boolean round = cardRect(0).contains(x, e.getY());
            if (square || round) {
                onPick.pick(square);
                invalidate();
                return true;
            }
        }
        return true;
    }
}

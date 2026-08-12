package org.telegram.ui.Components;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.MotionEvent;
import android.view.View;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.DevGramConfig;
import org.telegram.ui.ActionBar.Theme;

// DevGram: превью формы плавающей кнопки (как FabShapeCell в exteraGram) — две карточки-мини-экрана:
// пара бледных строк-заготовок + сама кнопка «+» в углу (круглая слева, квадратная справа).
// Выбранная карточка обведена акцентом; тап выбирает форму.
public class DevGramFabPreviewCell extends View {

    public interface OnPick { void pick(boolean square); }

    private final Paint card = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint outline = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint row = new Paint(Paint.ANTI_ALIAS_FLAG);
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
        plus.setStrokeWidth(AndroidUtilities.dp(2f));
        plus.setStrokeCap(Paint.Cap.ROUND);
        plus.setColor(Color.WHITE);
    }

    public void setOnPick(OnPick p) { onPick = p; }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(104), MeasureSpec.EXACTLY));
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
        int grey = Theme.getColor(Theme.key_windowBackgroundWhiteGrayText);
        int gr = Color.red(grey), gg = Color.green(grey), gb = Color.blue(grey);
        boolean square = DevGramConfig.squareFab;

        for (int i = 0; i < 2; i++) {
            boolean isSquareCard = i == 1;
            RectF cr = cardRect(i);
            card.setColor(Color.argb(22, gr, gg, gb));
            canvas.drawRoundRect(cr, AndroidUtilities.dp(12), AndroidUtilities.dp(12), card);
            if (isSquareCard == square) {
                outline.setColor(accent);
                rect.set(cr.left + AndroidUtilities.dp(1), cr.top + AndroidUtilities.dp(1),
                        cr.right - AndroidUtilities.dp(1), cr.bottom - AndroidUtilities.dp(1));
                canvas.drawRoundRect(rect, AndroidUtilities.dp(12), AndroidUtilities.dp(12), outline);
            }

            // две бледные «строки» (кружок + линия)
            float rx = cr.left + AndroidUtilities.dp(14);
            for (int rrow = 0; rrow < 2; rrow++) {
                float ry = cr.top + AndroidUtilities.dp(18) + rrow * AndroidUtilities.dp(20);
                float dotR = AndroidUtilities.dp(6);
                row.setColor(Color.argb(90, gr, gg, gb));
                canvas.drawCircle(rx + dotR, ry, dotR, row);
                float lineH = AndroidUtilities.dp(5);
                rect.set(rx + dotR * 2 + AndroidUtilities.dp(8), ry - lineH / 2f,
                        cr.right - AndroidUtilities.dp(30), ry + lineH / 2f);
                row.setColor(Color.argb(55, gr, gg, gb));
                canvas.drawRoundRect(rect, lineH / 2f, lineH / 2f, row);
            }

            // FAB в правом нижнем углу
            float fs = AndroidUtilities.dp(30);
            float fcx = cr.right - AndroidUtilities.dp(14) - fs / 2f;
            float fcy = cr.bottom - AndroidUtilities.dp(12) - fs / 2f;
            fab.setColor(Theme.getColor(Theme.key_chats_actionBackground));
            rect.set(fcx - fs / 2, fcy - fs / 2, fcx + fs / 2, fcy + fs / 2);
            float rad = isSquareCard ? AndroidUtilities.dp(10) : fs / 2f;
            canvas.drawRoundRect(rect, rad, rad, fab);
            float pl = AndroidUtilities.dp(7);
            canvas.drawLine(fcx - pl, fcy, fcx + pl, fcy, plus);
            canvas.drawLine(fcx, fcy - pl, fcx, fcy + pl, plus);
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        if (e.getAction() == MotionEvent.ACTION_UP && onPick != null) {
            if (cardRect(1).contains(e.getX(), e.getY())) { onPick.pick(true); invalidate(); }
            else if (cardRect(0).contains(e.getX(), e.getY())) { onPick.pick(false); invalidate(); }
        }
        return true;
    }
}

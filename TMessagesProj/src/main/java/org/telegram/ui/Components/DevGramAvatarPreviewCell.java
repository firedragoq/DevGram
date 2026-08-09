package org.telegram.ui.Components;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.View;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.ui.ActionBar.Theme;

// DevGram: превью формы аватара (как в exteraGram) — макет строки чата с аватаркой, текстом и
// онлайн-точкой; аватарка рисуется с текущим скруглением (AndroidUtilities.avatarCornerRadius).
public class DevGramAvatarPreviewCell extends View {

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();

    public DevGramAvatarPreviewCell(Context context) {
        super(context);
        setWillNotDraw(false);
        setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(84), MeasureSpec.EXACTLY));
    }

    @Override
    protected void onDraw(Canvas canvas) {
        int accent = Theme.getColor(Theme.key_switchTrack);
        int r = Color.red(accent), g = Color.green(accent), b = Color.blue(accent);
        float h = getMeasuredHeight();
        float w = getMeasuredWidth();
        boolean rtl = org.telegram.messenger.LocaleController.isRTL;

        float avSize = AndroidUtilities.dp(54);
        float avLeft = rtl ? w - AndroidUtilities.dp(16) - avSize : AndroidUtilities.dp(16);
        float avTop = (h - avSize) / 2f;

        // аватар — цветная плашка с текущим скруглением
        paint.setColor(Color.argb(255, r, g, b));
        rect.set(avLeft, avTop, avLeft + avSize, avTop + avSize);
        float rad = AndroidUtilities.avatarCornerRadius(avSize);
        canvas.drawRoundRect(rect, rad, rad, paint);

        // онлайн-точка в правом нижнем углу аватара
        float dotR = AndroidUtilities.dp(7);
        float dotCx = rtl ? avLeft + dotR : avLeft + avSize - dotR;
        float dotCy = avTop + avSize - dotR;
        paint.setColor(Theme.getColor(Theme.key_windowBackgroundWhite));
        canvas.drawCircle(dotCx, dotCy, dotR + AndroidUtilities.dp(2), paint);
        paint.setColor(Theme.getColor(Theme.key_chats_onlineCircle));
        canvas.drawCircle(dotCx, dotCy, dotR, paint);

        // две «строки текста» справа от аватара
        float tx = rtl ? AndroidUtilities.dp(16) : avLeft + avSize + AndroidUtilities.dp(14);
        float txEnd = rtl ? avLeft - AndroidUtilities.dp(14) : w - AndroidUtilities.dp(16);
        float lineH = AndroidUtilities.dp(9);
        paint.setColor(Color.argb(200, r, g, b));
        rect.set(tx, h / 2f - AndroidUtilities.dp(16), Math.min(txEnd, tx + AndroidUtilities.dp(120)), h / 2f - AndroidUtilities.dp(16) + lineH);
        canvas.drawRoundRect(rect, lineH / 2f, lineH / 2f, paint);
        paint.setColor(Color.argb(90, r, g, b));
        rect.set(tx, h / 2f + AndroidUtilities.dp(6), Math.min(txEnd, tx + AndroidUtilities.dp(180)), h / 2f + AndroidUtilities.dp(6) + lineH);
        canvas.drawRoundRect(rect, lineH / 2f, lineH / 2f, paint);
    }
}

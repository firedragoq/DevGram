package org.telegram.ui.Components;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.text.TextPaint;
import android.view.View;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.DevGramConfig;
import org.telegram.messenger.LocaleController;
import org.telegram.ui.ActionBar.Theme;

// DevGram: превью списка чатов (как ChatListPreviewCell в exteraGram) — макет шапки с заголовком
// (слева или по центру, отражает centerTitle) + пара строк чатов с аватарками в текущей форме.
public class DevGramChatListPreviewCell extends View {

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final TextPaint titlePaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();

    public DevGramChatListPreviewCell(Context context) {
        super(context);
        setWillNotDraw(false);
        setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
        titlePaint.setTypeface(AndroidUtilities.bold());
        titlePaint.setTextSize(AndroidUtilities.dp(16));
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(168), MeasureSpec.EXACTLY));
    }

    @Override
    protected void onDraw(Canvas canvas) {
        int accent = Theme.getColor(Theme.key_switchTrack);
        int r = Color.red(accent), g = Color.green(accent), b = Color.blue(accent);
        float w = getMeasuredWidth();
        boolean rtl = LocaleController.isRTL;

        // рамка-«карточка»
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(Color.argb(18, r, g, b));
        rect.set(AndroidUtilities.dp(14), AndroidUtilities.dp(10), w - AndroidUtilities.dp(14), getMeasuredHeight() - AndroidUtilities.dp(14));
        canvas.drawRoundRect(rect, AndroidUtilities.dp(12), AndroidUtilities.dp(12), paint);

        float pad = AndroidUtilities.dp(26);
        float barTop = AndroidUtilities.dp(22);

        // шапка: заголовок "DevGram" слева или по центру (centerTitle)
        titlePaint.setColor(Theme.getColor(Theme.key_actionBarDefaultTitle));
        String title = "DevGram";
        float tw = titlePaint.measureText(title);
        float tx = DevGramConfig.centerTitle ? (w - tw) / 2f : (rtl ? w - pad - tw : pad);
        canvas.drawText(title, tx, barTop + AndroidUtilities.dp(6), titlePaint);

        // две строки чатов
        float rowH = AndroidUtilities.dp(56);
        float first = barTop + AndroidUtilities.dp(20);
        for (int i = 0; i < 2; i++) {
            float cy = first + i * rowH + rowH / 2f;
            float avR = AndroidUtilities.dp(20);
            float avCx = rtl ? w - pad - avR : pad + avR;
            float avTop = cy - avR, avLeft = avCx - avR;
            paint.setColor(Color.argb(255, r, g, b));
            rect.set(avLeft, avTop, avLeft + avR * 2, avTop + avR * 2);
            float rad = AndroidUtilities.avatarCornerRadius(avR * 2);
            canvas.drawRoundRect(rect, rad, rad, paint);

            float lx = rtl ? pad : avCx + avR + AndroidUtilities.dp(14);
            float lxEnd = rtl ? avLeft - AndroidUtilities.dp(14) : w - pad;
            float lh = AndroidUtilities.dp(8);
            paint.setColor(Color.argb(200, r, g, b));
            rect.set(lx, cy - AndroidUtilities.dp(13), Math.min(lxEnd, lx + AndroidUtilities.dp(110)), cy - AndroidUtilities.dp(13) + lh);
            canvas.drawRoundRect(rect, lh / 2f, lh / 2f, paint);
            paint.setColor(Color.argb(90, r, g, b));
            rect.set(lx, cy + AndroidUtilities.dp(5), Math.min(lxEnd, lx + AndroidUtilities.dp(170)), cy + AndroidUtilities.dp(5) + lh);
            canvas.drawRoundRect(rect, lh / 2f, lh / 2f, paint);
        }
    }
}

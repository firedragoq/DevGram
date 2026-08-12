package org.telegram.ui.Components;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.View;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.ui.ActionBar.Theme;

// DevGram: превью формы аватара (как AvatarCornersPreviewCell в exteraGram) — бледная карточка с
// крупным серым аватаром (в текущей форме), зелёной онлайн-точкой и тремя серыми строками текста.
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
        super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(92), MeasureSpec.EXACTLY));
    }

    @Override
    protected void onDraw(Canvas canvas) {
        int grey = Theme.getColor(Theme.key_windowBackgroundWhiteGrayText);
        int gr = Color.red(grey), gg = Color.green(grey), gb = Color.blue(grey);
        float h = getMeasuredHeight();
        float w = getMeasuredWidth();
        boolean rtl = LocaleController.isRTL;

        // бледная карточка
        paint.setColor(Color.argb(20, gr, gg, gb));
        rect.set(AndroidUtilities.dp(14), AndroidUtilities.dp(8), w - AndroidUtilities.dp(14), h - AndroidUtilities.dp(8));
        canvas.drawRoundRect(rect, AndroidUtilities.dp(12), AndroidUtilities.dp(12), paint);

        float avSize = AndroidUtilities.dp(48);
        float avLeft = rtl ? w - AndroidUtilities.dp(28) - avSize : AndroidUtilities.dp(28);
        float avTop = (h - avSize) / 2f;

        // аватар — серая плашка с текущим скруглением
        paint.setColor(Color.argb(150, gr, gg, gb));
        rect.set(avLeft, avTop, avLeft + avSize, avTop + avSize);
        float rad = AndroidUtilities.avatarCornerRadius(avSize);
        canvas.drawRoundRect(rect, rad, rad, paint);

        // онлайн-точка в правом нижнем углу
        float dotR = AndroidUtilities.dp(6);
        float dotCx = rtl ? avLeft + dotR : avLeft + avSize - dotR;
        float dotCy = avTop + avSize - dotR;
        paint.setColor(Theme.getColor(Theme.key_windowBackgroundWhite));
        canvas.drawCircle(dotCx, dotCy, dotR + AndroidUtilities.dp(2), paint);
        paint.setColor(Theme.getColor(Theme.key_chats_onlineCircle));
        canvas.drawCircle(dotCx, dotCy, dotR, paint);

        // три серые строки справа
        float tx = rtl ? AndroidUtilities.dp(28) : avLeft + avSize + AndroidUtilities.dp(16);
        float txEnd = rtl ? avLeft - AndroidUtilities.dp(16) : w - AndroidUtilities.dp(28);
        float lineH = AndroidUtilities.dp(8);
        float[] widths = {AndroidUtilities.dp(70), AndroidUtilities.dp(150), AndroidUtilities.dp(110)};
        int[] alphas = {150, 70, 70};
        float startY = h / 2f - AndroidUtilities.dp(18);
        for (int i = 0; i < 3; i++) {
            paint.setColor(Color.argb(alphas[i], gr, gg, gb));
            float ly = startY + i * AndroidUtilities.dp(14);
            float lx = rtl ? txEnd - widths[i] : tx;
            rect.set(lx, ly, Math.min(txEnd, lx + widths[i]), ly + lineH);
            canvas.drawRoundRect(rect, lineH / 2f, lineH / 2f, paint);
        }
    }
}

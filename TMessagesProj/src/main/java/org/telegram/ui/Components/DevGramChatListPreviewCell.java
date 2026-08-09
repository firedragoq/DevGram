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
import org.telegram.messenger.MessagesController;
import org.telegram.ui.ActionBar.Theme;

// DevGram: превью «Список чатов» (как ChatListPreviewCell в exteraGram) — макет верхней панели
// диалогов: заголовок + эмодзи-статус + меню ⋮. Отражает «Заголовок по центру» и «Скрыть статус».
public class DevGramChatListPreviewCell extends View {

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint dots = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final TextPaint titlePaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();

    public DevGramChatListPreviewCell(Context context) {
        super(context);
        setWillNotDraw(false);
        setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
        titlePaint.setTypeface(AndroidUtilities.bold());
        titlePaint.setTextSize(AndroidUtilities.dp(17));
    }

    private static boolean hideStatus() {
        return MessagesController.getGlobalMainSettings().getBoolean("dg_hideStatus", false);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(72), MeasureSpec.EXACTLY));
    }

    @Override
    protected void onDraw(Canvas canvas) {
        int grey = Theme.getColor(Theme.key_windowBackgroundWhiteGrayText);
        int gr = Color.red(grey), gg = Color.green(grey), gb = Color.blue(grey);
        float w = getMeasuredWidth();
        float cy = getMeasuredHeight() / 2f;
        boolean rtl = LocaleController.isRTL;

        // карточка
        paint.setColor(Color.argb(20, gr, gg, gb));
        rect.set(AndroidUtilities.dp(14), AndroidUtilities.dp(10), w - AndroidUtilities.dp(14),
                getMeasuredHeight() - AndroidUtilities.dp(10));
        canvas.drawRoundRect(rect, AndroidUtilities.dp(12), AndroidUtilities.dp(12), paint);

        float sidePad = AndroidUtilities.dp(30);
        boolean showStatus = !hideStatus();

        // меню ⋮ у правого края
        dots.setColor(Theme.getColor(Theme.key_actionBarDefaultIcon));
        float dotX = rtl ? sidePad : w - sidePad;
        for (int i = -1; i <= 1; i++) {
            canvas.drawCircle(dotX, cy + i * AndroidUtilities.dp(6), AndroidUtilities.dp(2), dots);
        }

        // заголовок + эмодзи-статус
        titlePaint.setColor(Theme.getColor(Theme.key_actionBarDefaultTitle));
        String title = "DevGram";
        float tw = titlePaint.measureText(title);
        float emojiSize = showStatus ? AndroidUtilities.dp(18) : 0;
        float emojiGap = showStatus ? AndroidUtilities.dp(6) : 0;
        float blockW = tw + emojiGap + emojiSize;

        float bx = DevGramConfig.centerTitle ? (w - blockW) / 2f
                : (rtl ? w - sidePad - blockW : sidePad);
        canvas.drawText(title, bx, cy + AndroidUtilities.dp(6), titlePaint);
        if (showStatus) {
            // эмодзи-статус (премиум) — цветная скруглённая плашка рядом с именем
            paint.setColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlueText));
            float ex = bx + tw + emojiGap;
            rect.set(ex, cy - emojiSize / 2f, ex + emojiSize, cy + emojiSize / 2f);
            canvas.drawRoundRect(rect, AndroidUtilities.dp(4), AndroidUtilities.dp(4), paint);
        }
    }
}

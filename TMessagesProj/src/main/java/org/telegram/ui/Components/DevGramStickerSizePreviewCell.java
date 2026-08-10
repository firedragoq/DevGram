package org.telegram.ui.Components;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.text.TextPaint;
import android.view.View;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.DevGramConfig;
import org.telegram.messenger.Emoji;
import org.telegram.ui.ActionBar.Theme;

// DevGram: превью размера стикеров — мини-чат (обои + пузыри + стикер), стикер масштабируется слайдером.
public class DevGramStickerSizePreviewCell extends View {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final TextPaint text = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();
    private Drawable sticker;

    public DevGramStickerSizePreviewCell(Context context) {
        super(context);
        setWillNotDraw(false);
        setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
        text.setTextSize(AndroidUtilities.dp(13));
        sticker = Emoji.getEmojiBigDrawable("🐱"); // 🐱
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec,
                MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(190), MeasureSpec.EXACTLY));
    }

    @Override
    protected void onDraw(Canvas canvas) {
        int w = getWidth(), h = getHeight();
        // обои чата
        paint.setColor(Theme.getColor(Theme.key_chat_wallpaper));
        canvas.drawRect(0, 0, w, h, paint);

        float m = AndroidUtilities.dp(12);

        // входящий текстовый пузырь
        paint.setColor(Theme.getColor(Theme.key_chat_inBubble));
        rect.set(m, AndroidUtilities.dp(12), m + AndroidUtilities.dp(120), AndroidUtilities.dp(46));
        canvas.drawRoundRect(rect, AndroidUtilities.dp(12), AndroidUtilities.dp(12), paint);
        text.setColor(Theme.getColor(Theme.key_chat_messageTextIn));
        canvas.drawText("вау", rect.left + AndroidUtilities.dp(12), rect.top + AndroidUtilities.dp(22), text);

        // исходящее «фото» (плашка)
        paint.setColor(Color.argb(60, 0, 0, 0));
        float pw = AndroidUtilities.dp(96);
        rect.set(w - m - pw, AndroidUtilities.dp(12), w - m, AndroidUtilities.dp(12) + AndroidUtilities.dp(72));
        canvas.drawRoundRect(rect, AndroidUtilities.dp(12), AndroidUtilities.dp(12), paint);

        // стикер (масштаб от слайдера 2..14) с подписью-репли
        if (sticker == null) {
            sticker = Emoji.getEmojiBigDrawable("🐱");
        }
        float size = Math.max(2, Math.min(14, DevGramConfig.getStickerSize()));
        int max = AndroidUtilities.dp(90), min = AndroidUtilities.dp(30);
        int s = (int) (min + (max - min) * (size - 2f) / 12f);
        int cx = (int) (m + AndroidUtilities.dp(58));
        int cy = h - AndroidUtilities.dp(58);
        if (sticker != null) {
            sticker.setBounds(cx - s / 2, cy - s / 2, cx + s / 2, cy + s / 2);
            sticker.draw(canvas);
        }
        // маленький ярлык «Стикер» под стикером
        text.setColor(Theme.getColor(Theme.key_chat_inTimeText));
        canvas.drawText("🐈 Стикер", cx - AndroidUtilities.dp(28), h - AndroidUtilities.dp(10), text);
    }
}

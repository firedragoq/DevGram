package org.telegram.ui.Components;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.view.View;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.DevGramConfig;
import org.telegram.ui.ActionBar.Theme;

/** Live preview for DevGram chat message appearance options. */
public class DevGramMessagesPreviewCell extends View {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF bubble = new RectF();
    private final Path tail = new Path();

    public DevGramMessagesPreviewCell(Context context) {
        super(context);
        setWillNotDraw(false);
        setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
        text.setTypeface(AndroidUtilities.bold());
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec,
                MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(154), MeasureSpec.EXACTLY));
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        paint.setColor(Theme.getColor(Theme.key_chat_wallpaper));
        canvas.drawRect(0, 0, getWidth(), getHeight(), paint);
        drawBubble(canvas, false, 16, 18, 224, 70, "Привет! Это превью сообщения", "12:41");
        drawBubble(canvas, true, getWidth() / AndroidUtilities.density - 246, 86,
                getWidth() / AndroidUtilities.density - 16, 138, "Настройки видны сразу", "12:42");
    }

    private void drawBubble(Canvas canvas, boolean out, float lDp, float tDp, float rDp,
                            float bDp, String value, String time) {
        float l = AndroidUtilities.dp(lDp), t = AndroidUtilities.dp(tDp);
        float r = AndroidUtilities.dp(rDp), b = AndroidUtilities.dp(bDp);
        bubble.set(l, t, r, b);
        paint.setColor(Theme.getColor(out ? Theme.key_chat_outBubble : Theme.key_chat_inBubble));
        float radius = AndroidUtilities.dp(15);
        canvas.drawRoundRect(bubble, radius, radius, paint);
        if (!DevGramConfig.removeMessageTail) {
            tail.reset();
            if (out) {
                tail.moveTo(r - AndroidUtilities.dp(7), b - AndroidUtilities.dp(14));
                tail.lineTo(r + AndroidUtilities.dp(6), b);
                tail.lineTo(r - AndroidUtilities.dp(9), b);
            } else {
                tail.moveTo(l + AndroidUtilities.dp(7), b - AndroidUtilities.dp(14));
                tail.lineTo(l - AndroidUtilities.dp(6), b);
                tail.lineTo(l + AndroidUtilities.dp(9), b);
            }
            tail.close();
            canvas.drawPath(tail, paint);
        }
        text.setTypeface(null);
        text.setTextSize(AndroidUtilities.dp(14));
        text.setColor(Theme.getColor(out ? Theme.key_chat_messageTextOut : Theme.key_chat_messageTextIn));
        canvas.drawText(value, l + AndroidUtilities.dp(12), t + AndroidUtilities.dp(23), text);
        text.setTextSize(AndroidUtilities.dp(11));
        text.setColor(Theme.getColor(out ? Theme.key_chat_outTimeText : Theme.key_chat_inTimeText));
        String status = DevGramConfig.replaceEditedWithIcon ? "✎  " + time : "изменено  " + time;
        canvas.drawText(status, r - AndroidUtilities.dp(78), b - AndroidUtilities.dp(9), text);
        if (!DevGramConfig.hideShareButton && !out) {
            paint.setColor(Theme.getColor(Theme.key_chat_serviceText));
            canvas.drawCircle(r + AndroidUtilities.dp(18), b - AndroidUtilities.dp(14),
                    AndroidUtilities.dp(12), paint);
            text.setTextSize(AndroidUtilities.dp(14));
            text.setColor(Theme.getColor(Theme.key_chat_serviceBackground));
            canvas.drawText("↗", r + AndroidUtilities.dp(12), b - AndroidUtilities.dp(9), text);
        }
    }
}

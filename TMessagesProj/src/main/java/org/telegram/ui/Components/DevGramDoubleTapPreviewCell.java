package org.telegram.ui.Components;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.View;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.Emoji;
import org.telegram.messenger.MediaDataController;
import org.telegram.messenger.UserConfig;
import org.telegram.ui.ActionBar.Theme;

// DevGram: превью двойного нажатия — два пузыря (входящий/исходящий), по двойному тапу
// на пузырь всплывает выбранная реакция (как у exteraGram DoubleTapCell).
public class DevGramDoubleTapPreviewCell extends View {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF in = new RectF();
    private final RectF out = new RectF();
    private final GestureDetector gesture;
    private float popIn, popOut;
    private ValueAnimator anim;

    public DevGramDoubleTapPreviewCell(Context context) {
        super(context);
        setWillNotDraw(false);
        setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
        gesture = new GestureDetector(context, new GestureDetector.SimpleOnGestureListener() {
            @Override public boolean onDown(MotionEvent e) { return true; }
            @Override public boolean onDoubleTap(MotionEvent e) {
                boolean rightSide = e.getX() > getWidth() / 2f;
                play(rightSide);
                return true;
            }
        });
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec,
                MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(150), MeasureSpec.EXACTLY));
    }

    private void play(boolean rightSide) {
        if (anim != null) anim.cancel();
        anim = ValueAnimator.ofFloat(0f, 1f);
        anim.setDuration(750);
        anim.addUpdateListener(a -> {
            float v = (float) a.getAnimatedValue();
            if (rightSide) popOut = v; else popIn = v;
            invalidate();
        });
        anim.start();
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        gesture.onTouchEvent(event);
        return true;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        int w = getWidth();
        paint.setColor(Theme.getColor(Theme.key_chat_wallpaper));
        canvas.drawRect(0, 0, w, getHeight(), paint);

        // входящий пузырь (слева)
        in.set(AndroidUtilities.dp(14), AndroidUtilities.dp(20),
                AndroidUtilities.dp(170), AndroidUtilities.dp(64));
        paint.setColor(Theme.getColor(Theme.key_chat_inBubble));
        canvas.drawRoundRect(in, AndroidUtilities.dp(14), AndroidUtilities.dp(14), paint);
        drawHeart(canvas, in.centerX(), in.centerY(), false);

        // исходящий пузырь (справа)
        out.set(w - AndroidUtilities.dp(170), AndroidUtilities.dp(80),
                w - AndroidUtilities.dp(14), AndroidUtilities.dp(124));
        paint.setColor(Theme.getColor(Theme.key_chat_outBubble));
        canvas.drawRoundRect(out, AndroidUtilities.dp(14), AndroidUtilities.dp(14), paint);
        drawHeart(canvas, out.centerX(), out.centerY(), true);

        // всплывающие реакции
        if (popIn > 0f) drawReaction(canvas, in.right - AndroidUtilities.dp(24), in.top, popIn);
        if (popOut > 0f) drawReaction(canvas, out.right - AndroidUtilities.dp(24), out.top, popOut);
    }

    // контурное сердечко-подсказка внутри пузыря
    private void drawHeart(Canvas canvas, float cx, float cy, boolean out) {
        paint.setColor(Theme.getColor(out ? Theme.key_chat_outTimeText : Theme.key_chat_inTimeText));
        paint.setTextSize(AndroidUtilities.dp(20));
        String heart = "♡";
        float tw = paint.measureText(heart);
        canvas.drawText(heart, cx - tw / 2f, cy + AndroidUtilities.dp(7), paint);
    }

    private void drawReaction(Canvas canvas, float rx, float ty, float pop) {
        Drawable d = reactionDrawable();
        if (d == null) return;
        float up = Math.min(1f, pop * 2f);
        float fade = pop > 0.7f ? (pop - 0.7f) / 0.3f : 0f;
        int sz = (int) (AndroidUtilities.dp(32) * up);
        float ry = ty + AndroidUtilities.dp(12) - AndroidUtilities.dp(24) * up;
        d.setAlpha((int) (255 * (1f - fade)));
        d.setBounds((int) (rx - sz / 2f), (int) (ry - sz / 2f), (int) (rx + sz / 2f), (int) (ry + sz / 2f));
        d.draw(canvas);
        d.setAlpha(255);
    }

    private Drawable reactionDrawable() {
        String reaction = null;
        try {
            reaction = MediaDataController.getInstance(UserConfig.selectedAccount).getDoubleTapReaction();
        } catch (Throwable ignore) {
        }
        Drawable d = null;
        if (reaction != null && !reaction.startsWith("animated_")) {
            d = Emoji.getEmojiBigDrawable(reaction);
        }
        if (d == null) {
            d = Emoji.getEmojiBigDrawable("❤"); // ❤
        }
        return d;
    }
}

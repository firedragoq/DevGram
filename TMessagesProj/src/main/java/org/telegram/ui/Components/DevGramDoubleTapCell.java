package org.telegram.ui.Components;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;

import androidx.core.content.ContextCompat;
import androidx.core.graphics.ColorUtils;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.DevGramDoubleTapUtils;
import org.telegram.ui.ActionBar.Theme;

// DevGram: превью двойного нажатия как у exteraGram — два схематичных пузыря (входящий/исходящий)
// с иконкой текущего действия и анимацией-пульсацией при смене. Порт их DoubleTapCell.
public class DevGramDoubleTapCell extends LinearLayout {

    private static final int ICON_HALF = AndroidUtilities.dp(12); // половина размера иконки (иконка ~24dp)

    private final RectF rect = new RectF();
    private final Paint outlinePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint bubblePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint[] circleOutlinePaint = new Paint[2];
    private final Theme.MessageDrawable[] messages;
    private final FrameLayout preview;

    private final int[] actionIcon = new int[2];
    private final float[] iconChangingProgress = new float[2];
    private final float[] circleSizeProgress = new float[4];
    private final float[] circleProgress = new float[4];

    private final ValueAnimator[] animator = new ValueAnimator[2];

    public DevGramDoubleTapCell(final Context context) {
        super(context);
        messages = new Theme.MessageDrawable[]{
                new Theme.MessageDrawable(0, false, false),
                new Theme.MessageDrawable(0, true, false)
        };
        setWillNotDraw(false);
        setOrientation(VERTICAL);
        setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
        setPadding(AndroidUtilities.dp(13), 0, AndroidUtilities.dp(13), AndroidUtilities.dp(10));

        outlinePaint.setStyle(Paint.Style.STROKE);
        outlinePaint.setStrokeWidth(AndroidUtilities.dp(1) / 2f);
        outlinePaint.setColor(Theme.getColor(Theme.key_divider));

        preview = new FrameLayout(context) {
            @Override
            protected void onDraw(Canvas canvas) {
                Rect r = new Rect();
                float sw = outlinePaint.getStrokeWidth() / 2f;
                int bubbleFill = ColorUtils.blendARGB(
                        Theme.getColor(Theme.key_windowBackgroundWhite),
                        Theme.getColor(Theme.key_windowBackgroundGray), 0.6f);
                int accent = Theme.getColor(Theme.key_switchTrack);
                int iconColor = Theme.getColor(Theme.key_windowBackgroundWhiteGrayIcon);

                for (int i = 0; i < 2; i++) {
                    if (i == 0) {
                        rect.set(AndroidUtilities.dp(8) + sw, AndroidUtilities.dp(10) + sw,
                                (getMeasuredWidth() / 2f) - AndroidUtilities.dp(8) - sw, AndroidUtilities.dp(75) - sw);
                    } else {
                        canvas.translate(0, AndroidUtilities.dp(80));
                        rect.set((getMeasuredWidth() / 2f) + sw + AndroidUtilities.dp(8), AndroidUtilities.dp(5) + sw,
                                getMeasuredWidth() - AndroidUtilities.dp(8) - sw, AndroidUtilities.dp(70) - sw);
                    }
                    rect.round(r);
                    messages[i].setBounds(r);
                    bubblePaint.setStyle(Paint.Style.FILL);
                    bubblePaint.setColor(bubbleFill);
                    messages[i].draw(canvas, bubblePaint);
                    messages[i].draw(canvas, outlinePaint);

                    // пульсирующие кольца вокруг иконки
                    for (int c = 0; c < 2; c++) {
                        if (circleOutlinePaint[c] == null) {
                            circleOutlinePaint[c] = new Paint(Paint.ANTI_ALIAS_FLAG);
                            circleOutlinePaint[c].setStyle(Paint.Style.STROKE);
                        }
                        int idx = i + (c * 2);
                        circleOutlinePaint[c].setColor(ColorUtils.blendARGB(0, accent, circleProgress[idx]));
                        circleOutlinePaint[c].setStrokeWidth(AndroidUtilities.dp(1.5f) * circleProgress[idx] * circleProgress[idx]);
                        float cx = ((i == 0 ? 1 : 3) * getMeasuredWidth()) / 4f;
                        float cy = getMeasuredHeight() / 4f + AndroidUtilities.dpf2(i == 0 ? 3f : -2f);
                        canvas.drawCircle(cx, cy, AndroidUtilities.dp(25 - (c * 6)) * circleSizeProgress[idx], circleOutlinePaint[c]);
                    }

                    // иконка действия
                    Drawable d = ContextCompat.getDrawable(context, actionIcon[i]);
                    if (d != null) {
                        int cx, cy;
                        if (i == 0) {
                            cx = getMeasuredWidth() / 4;
                            cy = (int) (getMeasuredHeight() / 4 + AndroidUtilities.dpf2(3f));
                        } else {
                            cx = (getMeasuredWidth() * 3) / 4;
                            cy = (int) (getMeasuredHeight() / 4 - AndroidUtilities.dpf2(2f));
                        }
                        int grow = AndroidUtilities.dp(4 - (iconChangingProgress[i] * 4f));
                        d.setBounds(cx - ICON_HALF - grow, cy - ICON_HALF - grow, cx + ICON_HALF + grow, cy + ICON_HALF + grow);
                        d.setColorFilter(new PorterDuffColorFilter(
                                ColorUtils.blendARGB(Color.TRANSPARENT, iconColor, iconChangingProgress[i]),
                                PorterDuff.Mode.SRC_IN));
                        d.draw(canvas);
                    }
                }
            }
        };
        preview.setWillNotDraw(false);
        addView(preview, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));

        updateIcons(0, false);
        updateIcons(1, false);
    }

    // side: 0 — входящие, 1 — исходящие
    public void updateIcons(final int side, boolean animate) {
        boolean out = side == 1;
        if (!animate) {
            circleSizeProgress[side] = 0f;
            circleSizeProgress[side + 2] = 0f;
            circleProgress[side] = 0f;
            circleProgress[side + 2] = 0f;
            iconChangingProgress[side] = 1f;
            actionIcon[side] = DevGramDoubleTapUtils.currentIcon(out);
            invalidate();
            return;
        }

        // пульсация двух колец (у каждого кольца свой индекс idx — конфликтов нет)
        for (int c = 0; c < 2; c++) {
            final int idx = side + (c * 2);
            ValueAnimator size = ValueAnimator.ofFloat(0f, 1f);
            size.setDuration(1300);
            size.setStartDelay(60L * c);
            size.setInterpolator(Easings.easeInOutQuad);
            size.addUpdateListener(a -> {
                circleSizeProgress[idx] = (float) a.getAnimatedValue();
                invalidate();
            });

            ValueAnimator alpha = ValueAnimator.ofFloat(0f, 1f);
            alpha.setDuration(700);
            alpha.setStartDelay(c * 80L + 150L);
            alpha.setInterpolator(Easings.easeInOutQuad);
            alpha.addUpdateListener(a -> {
                circleProgress[idx] = (float) a.getAnimatedValue();
                invalidate();
            });
            alpha.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    ValueAnimator back = ValueAnimator.ofFloat(1f, 0f);
                    back.setDuration(700);
                    back.setInterpolator(Easings.easeInOutQuad);
                    back.addUpdateListener(a -> {
                        circleProgress[idx] = (float) a.getAnimatedValue();
                        invalidate();
                    });
                    back.start();
                }
            });
            size.start();
            alpha.start();
        }

        // смена иконки: сжать -> подменить -> вернуть
        if (animator[side] != null) animator[side].cancel();
        animator[side] = ValueAnimator.ofFloat(1f, 0f);
        animator[side].setDuration(250);
        animator[side].setInterpolator(Easings.easeInOutQuad);
        animator[side].addUpdateListener(a -> {
            iconChangingProgress[side] = (float) a.getAnimatedValue();
            invalidate();
        });
        animator[side].addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                actionIcon[side] = DevGramDoubleTapUtils.currentIcon(out);
                ValueAnimator back = ValueAnimator.ofFloat(0f, 1f);
                back.setDuration(250);
                back.setInterpolator(Easings.easeInOutQuad);
                back.addUpdateListener(a -> {
                    iconChangingProgress[side] = (float) a.getAnimatedValue();
                    invalidate();
                });
                back.addListener(new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(Animator a2) {
                        try {
                            performHapticFeedback(3, 2);
                        } catch (Exception ignore) {
                        }
                    }
                });
                back.start();
            }
        });
        animator[side].start();
    }

    @Override
    public void invalidate() {
        super.invalidate();
        preview.invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (Theme.dividerPaint != null) {
            canvas.drawLine(0, getMeasuredHeight() - 1, getMeasuredWidth(), getMeasuredHeight() - 1, Theme.dividerPaint);
        }
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(
                MeasureSpec.makeMeasureSpec(MeasureSpec.getSize(widthMeasureSpec), MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(170), MeasureSpec.EXACTLY));
    }
}

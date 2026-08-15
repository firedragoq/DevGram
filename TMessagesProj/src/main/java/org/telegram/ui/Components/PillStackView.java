package org.telegram.ui.Components;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.content.Context;
import android.view.MotionEvent;
import android.view.ViewConfiguration;
import android.widget.FrameLayout;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.ui.pillstack.PillStackConfig;
import org.telegram.ui.pillstack.pills.BasePill;

import java.util.ArrayList;
import java.util.List;

/** DevGram: порт PillStackView из exteraGram — вертикальный свайп между активными виджетами в строке поиска. */
public class PillStackView extends FrameLayout {
    private ValueAnimator currentAnimator;
    private int currentIndex = 0;
    private float currentSwipeProgress = 0f;
    private boolean isSwiping;
    private boolean isSwipingUp;
    private boolean longClickPerformed;
    private boolean maybeClick;
    private final List<BasePill> pills = new ArrayList<>();
    private boolean stackOnScreen = true;
    private float startX;
    private float startY;
    private final float touchSlop;
    private float visibilityFactor = -1f;
    private final Runnable longPressRunnable;

    public PillStackView(Context context) {
        super(context);
        touchSlop = ViewConfiguration.get(context).getScaledTouchSlop();
        setClipChildren(false);
        longPressRunnable = () -> {
            if (!maybeClick || isSwiping || pills.isEmpty()) return;
            longClickPerformed = pills.get(currentIndex).onPillLongClicked();
            if (longClickPerformed) performHapticFeedback(0);
        };
    }

    public void addPill(BasePill pill) {
        pills.add(pill);
        addView(pill);
        if (pills.size() - 1 != currentIndex) {
            pill.setAlpha(0f);
            pill.setScaleX(0.8f);
            pill.setScaleY(0.8f);
            pill.setVisibility(GONE);
        } else {
            pill.setVisibility(VISIBLE);
            pill.onPillSelected();
        }
        pill.onStackVisibilityChanged(stackOnScreen);
    }

    @Override
    public void onVisibilityAggregated(boolean isVisible) {
        super.onVisibilityAggregated(isVisible);
        if (stackOnScreen == isVisible) return;
        stackOnScreen = isVisible;
        for (BasePill pill : pills) pill.onStackVisibilityChanged(isVisible);
    }

    public int getPillsCount() { return pills.size(); }

    public void setCurrentIndex(int index) {
        if (index < 0 || index >= pills.size() || index == currentIndex) return;
        BasePill old = pills.get(currentIndex);
        old.setVisibility(GONE);
        old.onPillUnselected();
        currentIndex = index;
        BasePill next = pills.get(index);
        next.setVisibility(VISIBLE);
        next.setAlpha(1f);
        next.setScaleX(1f);
        next.setScaleY(1f);
        next.setTranslationY(0f);
        next.onPillSelected();
        requestLayout();
    }

    public void clearPills() {
        if (!pills.isEmpty() && currentIndex < pills.size()) pills.get(currentIndex).onPillUnselected();
        pills.clear();
        removeAllViews();
        currentIndex = 0;
    }

    public void setVisibilityFactor(float f) {
        if (visibilityFactor == f) return;
        visibilityFactor = f;
        if (f > 0.01f) {
            if (getVisibility() != VISIBLE) setVisibility(VISIBLE);
            setAlpha(visibilityFactor);
            setScaleX(AndroidUtilities.lerp(0.6f, 1.0f, visibilityFactor));
            setScaleY(AndroidUtilities.lerp(0.6f, 1.0f, visibilityFactor));
        } else {
            setVisibility(GONE);
        }
    }

    public void updateColors() {
        for (BasePill pill : pills) pill.updateColors();
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent event) {
        if (pills.isEmpty()) return super.onInterceptTouchEvent(event);
        int action = event.getActionMasked();
        if (action == MotionEvent.ACTION_DOWN) {
            startX = event.getRawX();
            startY = event.getRawY();
            isSwiping = false;
        } else if (action == MotionEvent.ACTION_MOVE) {
            float dx = event.getRawX() - startX;
            float dy = event.getRawY() - startY;
            if ((Math.abs(dy) > touchSlop || Math.abs(dx) > touchSlop) && Math.abs(dy) > touchSlop && pills.size() > 1) {
                isSwiping = true;
                if (currentAnimator != null) currentAnimator.cancel();
                startY = event.getRawY() - (isSwipingUp ? -(currentSwipeProgress * getHeight()) : getHeight() * currentSwipeProgress);
                if (getParent() != null) getParent().requestDisallowInterceptTouchEvent(true);
                return true;
            }
        }
        return super.onInterceptTouchEvent(event);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (pills.isEmpty()) return super.onTouchEvent(event);
        int action = event.getActionMasked();
        switch (action) {
            case MotionEvent.ACTION_DOWN:
                startX = event.getRawX();
                startY = event.getRawY();
                maybeClick = true;
                longClickPerformed = false;
                if (currentIndex < pills.size()) pills.get(currentIndex).setPressed(true);
                postDelayed(longPressRunnable, ViewConfiguration.getLongPressTimeout());
                return true;
            case MotionEvent.ACTION_MOVE: {
                float dx = event.getRawX() - startX;
                float dy = event.getRawY() - startY;
                if (!isSwiping && Math.abs(dy) > touchSlop && Math.abs(dy) > Math.abs(dx) && pills.size() > 1) {
                    isSwiping = true;
                    maybeClick = false;
                    removeCallbacks(longPressRunnable);
                    if (currentIndex < pills.size()) pills.get(currentIndex).setPressed(false);
                    if (currentAnimator != null) currentAnimator.cancel();
                    startY = event.getRawY();
                    if (getParent() != null) getParent().requestDisallowInterceptTouchEvent(true);
                }
                if (isSwiping) {
                    handleSwipeProgress(event.getRawY() - startY);
                } else if (maybeClick && (Math.abs(dx) > touchSlop || Math.abs(dy) > touchSlop)) {
                    maybeClick = false;
                    removeCallbacks(longPressRunnable);
                    if (currentIndex < pills.size()) pills.get(currentIndex).setPressed(false);
                }
                return true;
            }
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                removeCallbacks(longPressRunnable);
                if (currentIndex < pills.size()) pills.get(currentIndex).setPressed(false);
                if (isSwiping) {
                    finishSwipe(event.getRawY() - startY);
                } else if (maybeClick && !longClickPerformed && action == MotionEvent.ACTION_UP) {
                    if (currentIndex < pills.size()) pills.get(currentIndex).onPillClicked();
                }
                isSwiping = false;
                maybeClick = false;
                return true;
        }
        return true;
    }

    private void handleSwipeProgress(float f) {
        if (pills.size() <= 1) return;
        int height = getHeight();
        if (height <= 0) return;
        isSwipingUp = f < 0f;
        float abs = Math.abs(f) / height;
        int next = isSwipingUp ? currentIndex + 1 : currentIndex - 1;
        if (!PillStackConfig.getInfiniteScrolling() && (next >= pills.size() || next < 0)) {
            currentSwipeProgress = abs;
        } else {
            currentSwipeProgress = Math.min(abs, 1.0f);
        }
        applyProgress(currentSwipeProgress, isSwipingUp);
    }

    private void applyProgress(float f, boolean up) {
        BasePill current = pills.get(currentIndex);
        int size = up ? currentIndex + 1 : currentIndex - 1;
        if (PillStackConfig.getInfiniteScrolling()) {
            if (size >= pills.size()) size = 0;
            if (size < 0) size = pills.size() - 1;
        }
        for (int i = 0; i < pills.size(); i++) {
            if (i != currentIndex && i != size && pills.get(i).getVisibility() != GONE) pills.get(i).setVisibility(GONE);
        }
        if (!PillStackConfig.getInfiniteScrolling() && (size >= pills.size() || size < 0)) {
            float overscroll = getHeight() * (float) (1.0 - (1.0 / ((f * 0.18f) + 1.0)));
            if (up) overscroll = -overscroll;
            current.setTranslationY(overscroll);
            current.setAlpha(1f);
            return;
        }
        float min = Math.min(f, 1.0f);
        BasePill nextPill = pills.get(size);
        if (nextPill.getVisibility() != VISIBLE) nextPill.setVisibility(VISIBLE);
        float h = getHeight() * min;
        if (up) h = -h;
        current.setTranslationY(h);
        current.setAlpha(1.0f - min);
        float scaleDelta = 0.2f * min;
        float curScale = 1.0f - scaleDelta;
        current.setScaleX(curScale);
        current.setScaleY(curScale);
        float nextScale = scaleDelta + 0.8f;
        nextPill.setScaleX(nextScale);
        nextPill.setScaleY(nextScale);
        nextPill.setAlpha(min);
        float base = getHeight();
        if (!up) base = -base;
        nextPill.setTranslationY(base - (min * base));
    }

    private void finishSwipe(float f) {
        int height = getHeight();
        if (height <= 0) { cancelSwipe(isSwipingUp); return; }
        float threshold = height * 0.25f;
        boolean allowed = true;
        if (!PillStackConfig.getInfiniteScrolling()) {
            int next = isSwipingUp ? currentIndex + 1 : currentIndex - 1;
            if (next >= pills.size() || next < 0) allowed = false;
        }
        if (Math.abs(f) > threshold && allowed) {
            animateToNextPill(isSwipingUp);
        } else {
            cancelSwipe(isSwipingUp);
        }
    }

    private void animateToNextPill(final boolean up) {
        if (currentAnimator != null) currentAnimator.cancel();
        ValueAnimator anim = ValueAnimator.ofFloat(currentSwipeProgress, 1.0f);
        currentAnimator = anim;
        anim.setDuration(250L);
        anim.setInterpolator(CubicBezierInterpolator.EASE_OUT_QUINT);
        anim.addUpdateListener(a -> applyProgress((Float) a.getAnimatedValue(), up));
        anim.addListener(new AnimatorListenerAdapter() {
            private boolean cancelled = false;
            @Override public void onAnimationCancel(Animator animation) { cancelled = true; }
            @Override public void onAnimationEnd(Animator animation) {
                if (cancelled) return;
                BasePill old = pills.get(currentIndex);
                old.setVisibility(GONE);
                old.setPressed(false);
                old.setScaleX(1f);
                old.setScaleY(1f);
                old.onPillUnselected();
                currentIndex = up ? currentIndex + 1 : currentIndex - 1;
                if (PillStackConfig.getInfiniteScrolling()) {
                    if (currentIndex >= pills.size()) currentIndex = 0;
                    if (currentIndex < 0) currentIndex = pills.size() - 1;
                }
                for (int i = 0; i < pills.size(); i++) {
                    if (i != currentIndex) pills.get(i).setVisibility(GONE);
                }
                BasePill next = pills.get(currentIndex);
                next.setVisibility(VISIBLE);
                next.setScaleX(1f);
                next.setScaleY(1f);
                next.setTranslationY(0f);
                next.setAlpha(1f);
                next.onPillSelected();
                currentSwipeProgress = 0f;
                PillStackConfig.saveLastActivePillId(next.getPillId());
            }
        });
        anim.start();
    }

    private void cancelSwipe(final boolean up) {
        if (currentAnimator != null) currentAnimator.cancel();
        ValueAnimator anim = ValueAnimator.ofFloat(currentSwipeProgress, 0f);
        currentAnimator = anim;
        anim.setDuration(200L);
        anim.addUpdateListener(a -> applyProgress((Float) a.getAnimatedValue(), up));
        anim.addListener(new AnimatorListenerAdapter() {
            private boolean cancelled = false;
            @Override public void onAnimationCancel(Animator animation) { cancelled = true; }
            @Override public void onAnimationEnd(Animator animation) {
                if (cancelled) return;
                for (int i = 0; i < pills.size(); i++) {
                    if (i != currentIndex) {
                        BasePill p = pills.get(i);
                        p.setVisibility(GONE);
                        p.setPressed(false);
                        p.setScaleX(1f);
                        p.setScaleY(1f);
                    }
                }
                BasePill current = pills.get(currentIndex);
                current.setTranslationY(0f);
                current.setAlpha(1f);
                current.setScaleX(1f);
                current.setScaleY(1f);
                currentSwipeProgress = 0f;
            }
        });
        anim.start();
    }
}

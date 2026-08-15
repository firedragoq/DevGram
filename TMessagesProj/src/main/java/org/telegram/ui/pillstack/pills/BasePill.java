package org.telegram.ui.pillstack.pills;

import android.animation.TimeInterpolator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.os.SystemClock;
import android.transition.ChangeBounds;
import android.transition.TransitionManager;
import android.transition.TransitionSet;
import android.util.SparseArray;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import org.telegram.messenger.LocaleController;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.CubicBezierInterpolator;
import org.telegram.ui.Components.LoadingDrawable;

/** DevGram: порт BasePill из exteraGram — базовый контейнер виджета строки поиска с авто-обновлением. */
public abstract class BasePill extends FrameLayout {
    private static final SparseArray<Long> globalLastUpdateTimes = new SparseArray<>();
    private final Runnable autoRefreshRunnable;
    protected boolean loading;
    protected LoadingDrawable loadingDrawable;
    protected View loadingTargetView;
    private final RectF rectF = new RectF();
    protected Theme.ResourcesProvider resourcesProvider;
    private boolean stackVisible = true;

    public abstract int getPillId();
    public abstract long getRefreshInterval();
    public abstract void onPillClicked();
    public abstract boolean onPillLongClicked();
    public void onPillSelected() { }
    public void onPillUnselected() { }
    public abstract void onUpdateData(boolean forceRefresh);
    public abstract void updateColors();

    public BasePill(Context context, Theme.ResourcesProvider resourcesProvider) {
        super(context);
        this.resourcesProvider = resourcesProvider;
        this.autoRefreshRunnable = () -> { onUpdateData(false); scheduleNextUpdate(); };
        setLayoutParams(new FrameLayout.LayoutParams(-2, -2, (LocaleController.isRTL ? 3 : 5) | 16));
        setClipChildren(false);
        setClipToPadding(false);
    }

    private void scheduleNextUpdate() {
        removeCallbacks(autoRefreshRunnable);
        if (stackVisible) {
            long interval = getRefreshInterval();
            if (interval > 0) postDelayed(autoRefreshRunnable, interval);
        }
    }

    public boolean isRefreshDue() {
        long interval = getRefreshInterval();
        if (interval <= 0) return true;
        long last = globalLastUpdateTimes.get(getPillId(), 0L);
        return last == 0 || SystemClock.elapsedRealtime() - last >= interval;
    }

    @Override
    public void onAttachedToWindow() {
        super.onAttachedToWindow();
        if (stackVisible) {
            long interval = getRefreshInterval();
            if (interval > 0) {
                long now = SystemClock.elapsedRealtime();
                long last = globalLastUpdateTimes.get(getPillId(), 0L);
                if (last != 0) {
                    long d = now - last;
                    if (d < interval) { postDelayed(autoRefreshRunnable, interval - d); return; }
                }
                autoRefreshRunnable.run();
            }
        }
    }

    @Override
    public void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        removeCallbacks(autoRefreshRunnable);
    }

    public void onStackVisibilityChanged(boolean visible) {
        if (stackVisible == visible) return;
        stackVisible = visible;
        if (!visible) {
            removeCallbacks(autoRefreshRunnable);
        } else if (getRefreshInterval() > 0) {
            if (isRefreshDue()) onUpdateData(false);
            scheduleNextUpdate();
        }
    }

    public void markDataUpdated() {
        globalLastUpdateTimes.put(getPillId(), SystemClock.elapsedRealtime());
        scheduleNextUpdate();
    }

    public void setLoadingTargetView(View view) { this.loadingTargetView = view; }

    public void startLoading() {
        loading = true;
        if (loadingDrawable == null) {
            loadingDrawable = new LoadingDrawable(resourcesProvider);
            loadingDrawable.setCallback(this);
            loadingDrawable.setGradientScale(2.0f);
            loadingDrawable.setRadiiDp(14.0f);
            updateLoadingColors();
        }
        loadingDrawable.reset();
        loadingDrawable.resetDisappear();
        loadingDrawable.setAlpha(255);
        invalidate();
    }

    public void animateSizeChange() {
        if (isLaidOut() && getVisibility() == VISIBLE && getParent() != null && (getParent().getParent() instanceof ViewGroup)) {
            TransitionManager.beginDelayedTransition((ViewGroup) getParent().getParent(),
                    new TransitionSet().addTransition(new ChangeBounds()).setDuration(300L).setInterpolator((TimeInterpolator) CubicBezierInterpolator.EASE_OUT_QUINT));
        }
    }

    public void stopLoading() {
        loading = false;
        if (loadingDrawable != null) loadingDrawable.disappear();
    }

    public void updateLoadingColors() {
        if (loadingDrawable != null) {
            int color = Theme.getColor(Theme.key_windowBackgroundWhiteBlackText, resourcesProvider);
            loadingDrawable.setColors(Theme.multAlpha(color, 0.05f), Theme.multAlpha(color, 0.15f));
        }
    }

    public int getThemedColor(int key) { return Theme.getColor(key, resourcesProvider); }
    public int getThemedColor(int key, float alpha) { return Theme.multAlpha(getThemedColor(key), alpha); }

    @Override
    public void dispatchDraw(Canvas canvas) {
        super.dispatchDraw(canvas);
        if (loadingDrawable != null) {
            if (loadingDrawable.getAlpha() > 0 || !loadingDrawable.isDisappearing()) {
                View view = loadingTargetView != null ? loadingTargetView : this;
                rectF.set(view.getLeft(), view.getTop(), view.getRight(), view.getBottom());
                loadingDrawable.setBounds((int) rectF.left, (int) rectF.top, (int) rectF.right, (int) rectF.bottom);
                loadingDrawable.draw(canvas);
                invalidate();
            }
        }
    }

    @Override
    protected boolean verifyDrawable(Drawable who) {
        return who == loadingDrawable || super.verifyDrawable(who);
    }
}

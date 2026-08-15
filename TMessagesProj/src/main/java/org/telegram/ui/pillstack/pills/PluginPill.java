package org.telegram.ui.pillstack.pills;

import android.annotation.SuppressLint;
import android.content.Context;
import android.text.TextUtils;
import android.view.Gravity;
import android.widget.LinearLayout;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.AnimatedTextView;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.ScaleStateListAnimator;

/** DevGram: текстовая пилюля для плагинов (значение из колбэка Python-плагина). */
@SuppressLint("ViewConstructor")
public class PluginPill extends BasePill {
    public interface ValueProvider { String get(); }

    private final int pillId;
    private final ValueProvider valueProvider;
    private final Runnable clickCallback;
    private final Runnable longClickCallback;
    private final LinearLayout layout;
    private final AnimatedTextView textView;

    public PluginPill(Context context, Theme.ResourcesProvider rp, int pillId, String text,
                      ValueProvider valueProvider, Runnable clickCallback, Runnable longClickCallback) {
        super(context, rp);
        this.pillId = pillId;
        this.valueProvider = valueProvider;
        this.clickCallback = clickCallback;
        this.longClickCallback = longClickCallback;

        layout = new LinearLayout(context);
        layout.setOrientation(LinearLayout.HORIZONTAL);
        layout.setGravity(Gravity.CENTER);
        layout.setMinimumWidth(AndroidUtilities.dp(48));
        layout.setPadding(AndroidUtilities.dp(10), 0, AndroidUtilities.dp(10), 0);
        addView(layout, LayoutHelper.createFrame(-2, 28, (LocaleController.isRTL ? 3 : 5) | 16));

        textView = new AnimatedTextView(context, true, true, true);
        textView.setTextSize(AndroidUtilities.dp(13));
        textView.setTypeface(AndroidUtilities.bold());
        textView.setIncludeFontPadding(false);
        textView.adaptWidth = true;
        layout.addView(textView, LayoutHelper.createLinear(-2, -2, 16));

        setLoadingTargetView(layout);
        updateColors();
        ScaleStateListAnimator.apply(layout);
        textView.setText(text == null ? "" : text, false);
    }

    @Override public int getPillId() { return pillId; }
    @Override public long getRefreshInterval() { return 60000L; }

    @Override
    public void onUpdateData(boolean forceRefresh) {
        if (valueProvider == null) return;
        try {
            String value = valueProvider.get();
            if (value != null && !TextUtils.equals(textView.getText(), value)) {
                animateSizeChange();
                textView.setText(value, forceRefresh);
                // AnimatedTextView requests a new measure immediately when text grows,
                // but intentionally keeps the old width while a shrinking animation runs.
                // Re-measure after that animation so plugin pills also shrink in place.
                if (forceRefresh) {
                    textView.postDelayed(() -> {
                        textView.requestLayout();
                        layout.requestLayout();
                        requestLayout();
                    }, 320L);
                }
            }
            markDataUpdated();
        } catch (Throwable ignore) { }
    }

    @Override public void onPillClicked() { if (clickCallback != null) try { clickCallback.run(); } catch (Throwable ignore) { } }

    @Override
    public boolean onPillLongClicked() {
        if (longClickCallback == null) return false;
        try { longClickCallback.run(); return true; } catch (Throwable ignore) { return false; }
    }

    @Override
    public void setPressed(boolean pressed) {
        super.setPressed(pressed);
        layout.setPressed(pressed);
    }

    @Override
    public void updateColors() {
        int color = getThemedColor(Theme.key_windowBackgroundWhiteBlackText, 0.75f);
        layout.setBackground(Theme.createSimpleSelectorRoundRectDrawable(AndroidUtilities.dp(14),
                Theme.isCurrentThemeDark() ? getThemedColor(Theme.key_windowBackgroundWhite) : Theme.multAlpha(color, 0.09f),
                Theme.multAlpha(color, 0.1f)));
        textView.setTextColor(color);
        updateLoadingColors();
    }
}

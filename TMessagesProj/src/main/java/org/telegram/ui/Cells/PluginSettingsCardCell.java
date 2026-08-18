package org.telegram.ui.Cells;

import android.content.Context;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.LayoutHelper;

// DevGram: цветная карточка-баннер для настроек плагина (devgram.ui.Card) — значок-бейдж +
// заголовок/подзаголовок на заливке нужного цвета. Даёт разработчикам плагинов красивый
// баннер вместо голой строки списка, не трогая общий UniversalAdapter — используется через
// готовую точку расширения UItem.asCustom(id, view).
public class PluginSettingsCardCell extends FrameLayout {

    private final LinearLayout card;
    private final FrameLayout iconBadge;
    private final TextView iconView;
    private final TextView titleView;
    private final TextView subtitleView;
    private final TextView chevronView;

    public PluginSettingsCardCell(Context context) {
        super(context);

        card = new LinearLayout(context);
        card.setOrientation(LinearLayout.HORIZONTAL);
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setPadding(AndroidUtilities.dp(16), AndroidUtilities.dp(14), AndroidUtilities.dp(16), AndroidUtilities.dp(14));
        addView(card, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.FILL,
                14, 6, 14, 6));

        iconBadge = new FrameLayout(context);
        card.addView(iconBadge, LayoutHelper.createLinear(44, 44, 0, 0, 0, 14, 0));

        iconView = new TextView(context);
        iconView.setGravity(Gravity.CENTER);
        iconView.setTextSize(19);
        iconView.setTextColor(0xFFFFFFFF);
        iconBadge.addView(iconView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.FILL));

        LinearLayout info = new LinearLayout(context);
        info.setOrientation(LinearLayout.VERTICAL);
        card.addView(info, LayoutHelper.createLinear(0, LayoutHelper.WRAP_CONTENT, 1f));

        titleView = new TextView(context);
        titleView.setTextSize(16);
        titleView.setTextColor(0xFFFFFFFF);
        titleView.setTypeface(AndroidUtilities.bold());
        titleView.setMaxLines(1);
        titleView.setEllipsize(android.text.TextUtils.TruncateAt.END);
        info.addView(titleView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        subtitleView = new TextView(context);
        subtitleView.setTextSize(13);
        subtitleView.setTextColor(0xCCFFFFFF);
        subtitleView.setMaxLines(2);
        subtitleView.setEllipsize(android.text.TextUtils.TruncateAt.END);
        info.addView(subtitleView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 2, 0, 0));

        chevronView = new TextView(context);
        chevronView.setText("›");
        chevronView.setTextSize(22);
        chevronView.setTextColor(0x99FFFFFF);
        chevronView.setGravity(Gravity.CENTER);
        card.addView(chevronView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, 0, 8, 0, 0, 0));

        updateBackground(0xFF3B82F6);
    }

    // color — заливка карточки (ARGB); та же заливка чуть темнее идёт под значок.
    public void set(String icon, String title, String subtitle, int color) {
        iconView.setText(icon == null || icon.isEmpty() ? "✦" : icon);
        titleView.setText(title == null ? "" : title);
        if (subtitle == null || subtitle.isEmpty()) {
            subtitleView.setVisibility(GONE);
        } else {
            subtitleView.setVisibility(VISIBLE);
            subtitleView.setText(subtitle);
        }
        updateBackground(color);
    }

    private void updateBackground(int color) {
        card.setBackground(Theme.createSimpleSelectorRoundRectDrawable(AndroidUtilities.dp(16), color,
                Theme.multAlpha(0xFFFFFFFF, 0.12f)));

        GradientDrawable badgeBg = new GradientDrawable();
        badgeBg.setShape(GradientDrawable.OVAL);
        badgeBg.setColor(Theme.multAlpha(0xFFFFFFFF, 0.22f));
        iconBadge.setBackground(badgeBg);
    }
}

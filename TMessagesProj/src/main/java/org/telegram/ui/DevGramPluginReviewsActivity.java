package org.telegram.ui;

import android.content.Context;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.DevGramBadges;
import org.telegram.messenger.DevGramPlugins;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.ActionBar.BottomSheet;
import org.telegram.ui.Components.BulletinFactory;
import org.telegram.ui.Components.LayoutHelper;

import java.util.ArrayList;

public class DevGramPluginReviewsActivity extends BaseFragment {
    private final DevGramPlugins.CatalogEntry entry;
    private LinearLayout content;

    public DevGramPluginReviewsActivity(DevGramPlugins.CatalogEntry entry) {
        this.entry = entry;
    }

    @Override
    public View createView(Context context) {
        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setTitle("Отзывы · " + entry.name);
        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override public void onItemClick(int id) { if (id == -1) finishFragment(); }
        });
        content = new LinearLayout(context);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(AndroidUtilities.dp(16), AndroidUtilities.dp(12), AndroidUtilities.dp(16), AndroidUtilities.dp(28));
        content.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundGray, resourceProvider));
        ScrollView scroll = new ScrollView(context);
        scroll.addView(content);
        load(context);
        return fragmentView = scroll;
    }

    private void load(Context context) {
        DevGramPlugins.fetchReviews(entry.id, reviews -> {
            content.removeAllViews();
            TextView header = label(context, reviews.isEmpty() ? "Отзывов пока нет" : reviews.size() + " " + plural(reviews.size()), 20, true,
                    Theme.getColor(Theme.key_windowBackgroundWhiteBlackText, resourceProvider));
            content.addView(header, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 4, 0, 10));
            if (reviews.isEmpty()) {
                TextView empty = label(context, "Станьте первым, кто поделится впечатлением о плагине.", 15, false,
                        Theme.getColor(Theme.key_windowBackgroundWhiteGrayText, resourceProvider));
                empty.setGravity(Gravity.CENTER); empty.setPadding(AndroidUtilities.dp(24), AndroidUtilities.dp(40), AndroidUtilities.dp(24), AndroidUtilities.dp(40));
                content.addView(empty, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
                return;
            }
            for (DevGramPlugins.Review review : reviews) content.addView(reviewCard(context, review), LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 0, 0, 10));
        });
    }

    private View reviewCard(Context context, DevGramPlugins.Review review) {
        LinearLayout card = new LinearLayout(context); card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(AndroidUtilities.dp(16), AndroidUtilities.dp(12), AndroidUtilities.dp(10), AndroidUtilities.dp(14));
        card.setBackground(Theme.createRoundRectDrawable(AndroidUtilities.dp(18), Theme.getColor(Theme.key_windowBackgroundWhite, resourceProvider)));
        LinearLayout head = new LinearLayout(context); head.setGravity(Gravity.CENTER_VERTICAL);
        TextView title = label(context, review.name, 15, true, Theme.getColor(Theme.key_windowBackgroundWhiteBlackText, resourceProvider));
        head.addView(title, LayoutHelper.createLinear(0, LayoutHelper.WRAP_CONTENT, 1f));
        TextView menu = label(context, "⋮", 28, true, Theme.getColor(Theme.key_windowBackgroundWhiteGrayText, resourceProvider));
        menu.setGravity(Gravity.CENTER); menu.setContentDescription("Действия с отзывом");
        menu.setOnClickListener(v -> showActions(context, review, card));
        head.addView(menu, LayoutHelper.createLinear(44, 44)); card.addView(head);
        TextView stars = label(context, "★".repeat(Math.max(0, Math.min(5, review.rating))), 15, true, 0xFFE0A400);
        card.addView(stars, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, -5, 0, 7));
        TextView body = label(context, review.text, 15, false, Theme.getColor(Theme.key_windowBackgroundWhiteBlackText, resourceProvider));
        body.setLineSpacing(AndroidUtilities.dp(2), 1f); card.addView(body);
        return card;
    }

    private void showActions(Context context, DevGramPlugins.Review review, View card) {
        boolean own = review.userId == DevGramPlugins.myId();
        boolean developer = DevGramBadges.hasDeveloperFeatures(DevGramPlugins.myId());
        if (!own) {
            DevGramPlugins.hasReportedReview(entry.id, review.userId, reported -> showActionsResolved(context, review, card, developer, reported));
            return;
        }
        showActionsResolved(context, review, card, developer, false);
    }

    private void showActionsResolved(Context context, DevGramPlugins.Review review, View card, boolean developer, boolean reported) {
        boolean own = review.userId == DevGramPlugins.myId();
        LinearLayout root = new LinearLayout(context); root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(AndroidUtilities.dp(16), AndroidUtilities.dp(12), AndroidUtilities.dp(16), AndroidUtilities.dp(20));
        root.addView(label(context, own ? "Ваш отзыв" : "Действия с отзывом", 20, true,
                Theme.getColor(Theme.key_windowBackgroundWhiteBlackText, resourceProvider)), LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 4, 0, 12));
        BottomSheet[] ref = new BottomSheet[1];
        if (!own) addAction(root, context, reported ? "Вы уже подавали жалобу" : "Пожаловаться", reported ? "Повторная жалоба на этот отзыв недоступна" : "Сообщить команде DevGram о нарушении", false, () -> { ref[0].dismiss(); if(reported)BulletinFactory.of(this).createSimpleBulletin(R.raw.contact_check,"Вы уже подавали жалобу").show();else showReportReasonMenu(context, review.userId); });
        if (own || developer) addAction(root, context, "Удалить отзыв", own ? "Отзыв исчезнет из карточки плагина" : "Удаление доступно команде DevGram", true, () -> {
            if (own) DevGramPlugins.deleteOwnReview(entry.id, ok -> afterDelete(ok, card));
            else DevGramPlugins.deleteReviewAsDeveloper(entry.id, review.userId, ok -> afterDelete(ok, card));
            ref[0].dismiss();
        });
        BottomSheet.Builder builder = new BottomSheet.Builder(context); builder.setApplyBottomPadding(false); builder.setCustomView(root); ref[0] = builder.create(); ref[0].show();
    }

    private void afterDelete(boolean ok, View card) {
        if (ok) { card.setVisibility(View.GONE); BulletinFactory.of(this).createSimpleBulletin(R.raw.contact_check, "Отзыв удалён").show(); }
        else BulletinFactory.of(this).createErrorBulletin("Не удалось удалить отзыв").show();
    }

    private void showReportReasonMenu(Context context, long uid) {
        new org.telegram.ui.ActionBar.AlertDialog.Builder(context)
                .setTitle("Почему вы жалуетесь?")
                .setMessage("Жалоба будет отправлена команде DevGram на проверку.")
                .setItems(new CharSequence[]{"Спам или реклама", "Оскорбления и травля", "Ложная информация", "Другая причина"}, (d, which) -> {
                    if (which == 3) {
                        android.widget.EditText input = DevGramPluginReportsActivity.themedInput(context, "Что именно нарушает этот отзыв?");
                        new org.telegram.ui.ActionBar.AlertDialog.Builder(context).setTitle("Другая причина").setView(input)
                                .setPositiveButton("Отправить", (dd, ww) -> sendReport(uid, input.getText().toString())).setNegativeButton("Отмена", null).show();
                    } else sendReport(uid, which == 0 ? "Спам или реклама" : which == 1 ? "Оскорбления и травля" : "Ложная информация");
                }).setNegativeButton("Отмена", null).show();
    }

    private void sendReport(long uid, String reason) {
        reason = reason == null ? "" : reason.trim();
        if (reason.isEmpty()) { BulletinFactory.of(this).createErrorBulletin("Укажите причину жалобы").show(); return; }
        DevGramPlugins.reportReview(entry.id, uid, reason, ok -> {if(!ok)DevGramPlugins.hasReportedReview(entry.id,uid,reported->{if(reported)BulletinFactory.of(this).createSimpleBulletin(R.raw.contact_check,"Вы уже подавали жалобу").show();});BulletinFactory.of(this).createSimpleBulletin(R.raw.contact_check, ok ? "Жалоба отправлена" : "Не удалось отправить жалобу").show();});
    }

    private void addAction(LinearLayout root, Context context, String title, String subtitle, boolean danger, Runnable click) {
        LinearLayout row = new LinearLayout(context); row.setOrientation(LinearLayout.VERTICAL); row.setPadding(AndroidUtilities.dp(16), AndroidUtilities.dp(12), AndroidUtilities.dp(16), AndroidUtilities.dp(12));
        row.setBackground(Theme.createSimpleSelectorRoundRectDrawable(AndroidUtilities.dp(14), Theme.getColor(Theme.key_windowBackgroundGray, resourceProvider), Theme.getColor(Theme.key_listSelector, resourceProvider)));
        row.addView(label(context, title, 16, true, danger ? Theme.getColor(Theme.key_text_RedRegular, resourceProvider) : Theme.getColor(Theme.key_windowBackgroundWhiteBlackText, resourceProvider)));
        row.addView(label(context, subtitle, 13, false, Theme.getColor(Theme.key_windowBackgroundWhiteGrayText, resourceProvider)), LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 3, 0, 0));
        row.setOnClickListener(v -> click.run()); root.addView(row, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 0, 0, 8));
    }

    private TextView label(Context context, String value, int size, boolean bold, int color) { TextView text = new TextView(context); text.setText(value); text.setTextSize(size); text.setTextColor(color); if (bold) text.setTypeface(AndroidUtilities.bold()); return text; }
    private String plural(int count) { int n = count % 100, n1 = count % 10; return n > 10 && n < 20 ? "отзывов" : n1 == 1 ? "отзыв" : n1 >= 2 && n1 <= 4 ? "отзыва" : "отзывов"; }
}

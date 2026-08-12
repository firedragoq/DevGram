package org.telegram.ui;

import android.content.Context;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Button;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.DevGramPlugins;
import org.telegram.messenger.R;
import org.telegram.messenger.browser.Browser;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.BulletinFactory;
import org.telegram.ui.Components.LayoutHelper;

import java.util.ArrayList;

public class DevGramPluginDetailsActivity extends BaseFragment {
    private final DevGramPlugins.CatalogEntry entry;
    private LinearLayout content;
    private TextView ratingView;

    public DevGramPluginDetailsActivity(DevGramPlugins.CatalogEntry entry) {
        this.entry = entry;
    }

    @Override
    public View createView(Context context) {
        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setAllowOverlayTitle(true);
        actionBar.setTitle(entry.name);
        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override public void onItemClick(int id) { if (id == -1) finishFragment(); }
        });

        content = new LinearLayout(context);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(AndroidUtilities.dp(16), AndroidUtilities.dp(14), AndroidUtilities.dp(16), AndroidUtilities.dp(24));
        content.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundGray, resourceProvider));

        addText(entry.name, 24, true, Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
        if (!entry.author.isEmpty() && entry.author.contains("@")) {
            addLink("Автор: " + entry.author + (entry.version.isEmpty() ? "" : "  ·  v" + entry.version), entry.author);
        } else {
            addText((entry.author.isEmpty() ? "" : "Автор: " + entry.author) +
                    (entry.version.isEmpty() ? "" : "  ·  v" + entry.version), 14, false,
                    Theme.getColor(Theme.key_windowBackgroundWhiteGrayText));
        }
        if (!entry.channel.isEmpty()) addLink("🧩 " + entry.channel, entry.channel);
        if (entry.submittedAt > 0) {
            String dates = "Опубликован: " + android.text.format.DateFormat.format("dd.MM.yyyy", entry.submittedAt);
            if (entry.updatedAt > entry.submittedAt) dates += "  ·  Обновлён: " + android.text.format.DateFormat.format("dd.MM.yyyy", entry.updatedAt);
            addText(dates, 13, false, Theme.getColor(Theme.key_windowBackgroundWhiteGrayText));
        }
        if (!entry.desc.isEmpty()) addText(entry.desc, 16, false, Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));

        TextView install = button(context, DevGramPlugins.isInstalled(entry.id) ? "Обновить плагин" : "Установить плагин");
        install.setOnClickListener(v -> {
            if (DevGramPlugins.install(entry.source, entry.id, true))
                BulletinFactory.of(this).createSimpleBulletin(R.raw.contact_check, "Плагин установлен").show();
            else BulletinFactory.of(this).createErrorBulletin("Не удалось установить плагин").show();
        });
        content.addView(install, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 48, 0, 16, 0, 12));

        addText("Отзывы", 20, true, Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
        ratingView = addText("Загрузка рейтинга…", 14, false, Theme.getColor(Theme.key_windowBackgroundWhiteGrayText));
        DevGramPlugins.fetchReviews(entry.id, reviews -> {
            double sum = 0;
            for (DevGramPlugins.Review r : reviews) sum += r.rating;
            ratingView.setText(reviews.isEmpty() ? "Пока нет отзывов" : String.format(java.util.Locale.US, "%.1f ★  ·  %d отзывов", sum / reviews.size(), reviews.size()));
            int shown = 0;
            for (DevGramPlugins.Review r : reviews) {
                if (shown++ >= 3) break;
                TextView review = addText("★".repeat(Math.max(0, Math.min(5, r.rating))) + "  " + r.name + "\n" + r.text, 14, false, Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
                review.setBackground(Theme.createRoundRectDrawable(AndroidUtilities.dp(12), Theme.getColor(Theme.key_windowBackgroundWhite, resourceProvider)));
                review.setPadding(AndroidUtilities.dp(12), AndroidUtilities.dp(10), AndroidUtilities.dp(12), AndroidUtilities.dp(10));
                if (r.userId == DevGramPlugins.myId()) {
                    review.setOnClickListener(v -> showReviewDialog(context, r));
                    review.setOnLongClickListener(v -> {
                        new org.telegram.ui.ActionBar.AlertDialog.Builder(context).setTitle("Мой отзыв")
                                .setItems(new CharSequence[]{"Изменить", "Удалить"}, (d, which) -> {
                                    if (which == 0) { showReviewDialog(context, r); return; }
                                    DevGramPlugins.deleteOwnReview(entry.id, ok -> {
                                    if (ok) { review.setVisibility(View.GONE); BulletinFactory.of(this).createSimpleBulletin(R.raw.contact_check, "Отзыв удалён").show(); }
                                });}).setNegativeButton("Отмена", null).show();
                        return true;
                    });
                } else {
                    review.setOnLongClickListener(v -> {
                        boolean canModerate = org.telegram.messenger.DevGramBadges.hasDeveloperFeatures(DevGramPlugins.myId());
                        CharSequence[] actions = canModerate ? new CharSequence[]{"Пожаловаться", "Удалить отзыв"} : new CharSequence[]{"Спам", "Оскорбление", "Ложная информация", "Другая причина"};
                        new org.telegram.ui.ActionBar.AlertDialog.Builder(context).setTitle(canModerate ? "Управление отзывом" : "Пожаловаться на отзыв")
                                .setItems(actions, (d, which) -> {
                                    if (canModerate && which == 1) {
                                        DevGramPlugins.deleteReviewAsDeveloper(entry.id, r.userId, ok -> BulletinFactory.of(this).createSimpleBulletin(R.raw.contact_check, ok ? "Отзыв удалён" : "Нет прав или ошибка удаления").show());
                                    } else {
                                        if (canModerate || which == 3) {
                                            showCustomReportDialog(context, r.userId);
                                        } else {
                                            String reason = which == 0 ? "Спам" : which == 1 ? "Оскорбление" : "Ложная информация";
                                            sendReviewReport(r.userId, reason);
                                        }
                                    }
                                })
                                .setNegativeButton("Отмена", null).show();
                        return true;
                    });
                }
            }
        });

        TextView leave = button(context, "Оставить отзыв");
        leave.setOnClickListener(v -> showReviewDialog(context, null));
        content.addView(leave, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 46, 0, 12, 0, 0));
        TextView allReviews = new TextView(context);
        allReviews.setText("Все отзывы"); allReviews.setGravity(Gravity.CENTER); allReviews.setTextSize(14);
        allReviews.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlueText, resourceProvider)); allReviews.setPadding(0, AndroidUtilities.dp(12), 0, AndroidUtilities.dp(4));
        allReviews.setOnClickListener(v -> presentFragment(new DevGramPluginReviewsActivity(entry)));
        content.addView(allReviews, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
        TextView history = new TextView(context);
        history.setText("История публикации"); history.setGravity(Gravity.CENTER); history.setTextSize(14);
        history.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlueText, resourceProvider)); history.setPadding(0, AndroidUtilities.dp(14), 0, AndroidUtilities.dp(8));
        history.setOnClickListener(v -> presentFragment(new DevGramPluginHistoryActivity(entry.id, entry.name)));
        content.addView(history, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
        TextView report = new TextView(context);
        report.setText("Пожаловаться на плагин"); report.setGravity(Gravity.CENTER); report.setTextSize(14);
        report.setTextColor(Theme.getColor(Theme.key_text_RedRegular, resourceProvider)); report.setPadding(0, AndroidUtilities.dp(10), 0, AndroidUtilities.dp(8));
        report.setOnClickListener(v -> new org.telegram.ui.ActionBar.AlertDialog.Builder(context).setTitle("Жалоба на плагин")
                .setItems(new CharSequence[]{"Вредоносный код", "Спам или обман", "Нарушение авторских прав", "Не работает"}, (d, which) -> {
                    String[] reasons = {"malware", "spam", "copyright", "broken"};
                    DevGramPlugins.reportPlugin(entry.id, reasons[which], ok -> BulletinFactory.of(this).createSimpleBulletin(R.raw.contact_check, ok ? "Жалоба отправлена" : "Не удалось отправить жалобу").show());
                }).setNegativeButton("Отмена", null).show());
        content.addView(report, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
        ScrollView scroll = new ScrollView(context);
        scroll.setFillViewport(true);
        scroll.addView(content, LayoutHelper.createScroll(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.TOP));
        return fragmentView = scroll;
    }

    private TextView addText(String text, int size, boolean bold, int color) {
        TextView t = new TextView(getContext()); t.setText(text); t.setTextSize(size); t.setTextColor(color);
        if (bold) t.setTypeface(AndroidUtilities.bold());
        t.setPadding(0, AndroidUtilities.dp(3), 0, AndroidUtilities.dp(3));
        content.addView(t, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 0, 0, 2));
        return t;
    }

    private void addLink(String label, String target) {
        TextView t = addText(label, 14, false, Theme.getColor(Theme.key_windowBackgroundWhiteBlueText));
        t.setOnClickListener(v -> Browser.openUrl(getContext(), target.startsWith("http") ? target : "https://t.me/" + target.replace("@", "")));
    }

    private TextView button(Context c, String text) {
        TextView t = new TextView(c); t.setText(text); t.setGravity(Gravity.CENTER); t.setTypeface(AndroidUtilities.bold());
        t.setTextColor(Theme.getColor(Theme.key_featuredStickers_buttonText, resourceProvider));
        t.setBackground(Theme.createSimpleSelectorRoundRectDrawable(AndroidUtilities.dp(14), Theme.getColor(Theme.key_featuredStickers_addButton, resourceProvider), Theme.getColor(Theme.key_featuredStickers_addButtonPressed, resourceProvider)));
        return t;
    }

    private void showReviewDialog(Context context, DevGramPlugins.Review existing) {
        LinearLayout form = new LinearLayout(context);
        form.setOrientation(LinearLayout.VERTICAL);
        form.setPadding(AndroidUtilities.dp(24), 0, AndroidUtilities.dp(24), 0);
        LinearLayout stars = new LinearLayout(context);
        stars.setGravity(Gravity.CENTER);
        final int[] selected = {existing == null ? 5 : existing.rating};
        TextView[] starViews = new TextView[5];
        for (int i = 0; i < starViews.length; i++) {
            final int score = i + 1;
            TextView star = new TextView(context);
            star.setTextSize(32); star.setGravity(Gravity.CENTER); star.setTextColor(0xFFE0A400);
            star.setOnClickListener(v -> { selected[0] = score; updateStars(starViews, selected[0]); });
            starViews[i] = star;
            stars.addView(star, LayoutHelper.createLinear(46, 52));
        }
        updateStars(starViews, selected[0]);
        form.addView(stars, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 52));
        EditText input = new EditText(context); input.setHint("Ваш отзыв"); input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE); input.setMinLines(3);
        if (existing != null) input.setText(existing.text);
        form.addView(input, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
        new org.telegram.ui.ActionBar.AlertDialog.Builder(context).setTitle(existing == null ? "Отзыв о плагине" : "Изменить отзыв").setView(form)
                .setPositiveButton(existing == null ? "Отправить" : "Сохранить", (d, w) -> DevGramPlugins.saveReview(entry.id, selected[0], input.getText().toString(), ok -> {
                    BulletinFactory.of(this).createSimpleBulletin(R.raw.contact_check, ok ? "Отзыв сохранён" : "Не удалось сохранить отзыв").show();
                    if (ok) DevGramPlugins.fetchReviews(entry.id, reviews -> {
                        double sum = 0; for (DevGramPlugins.Review r : reviews) sum += r.rating;
                        if (ratingView != null) ratingView.setText(reviews.isEmpty() ? "Пока нет отзывов" : String.format(java.util.Locale.US, "%.1f ★  ·  %d отзывов", sum / reviews.size(), reviews.size()));
                    });
                }))
                .setNegativeButton("Отмена", null).show();
    }

    private void updateStars(TextView[] stars, int selected) {
        for (int i = 0; i < stars.length; i++) stars[i].setText(i < selected ? "★" : "☆");
    }

    private void showCustomReportDialog(Context context, long reviewUserId) {
        EditText input = new EditText(context);
        input.setHint("Опишите причину жалобы");
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        input.setMinLines(3);
        input.setMaxLines(6);
        new org.telegram.ui.ActionBar.AlertDialog.Builder(context)
                .setTitle("Причина жалобы")
                .setView(input)
                .setPositiveButton("Отправить", (d, w) -> {
                    String reason = input.getText() == null ? "" : input.getText().toString().trim();
                    if (reason.isEmpty()) BulletinFactory.of(this).createErrorBulletin("Напишите причину жалобы").show();
                    else sendReviewReport(reviewUserId, reason);
                })
                .setNegativeButton("Отмена", null).show();
    }

    private void sendReviewReport(long reviewUserId, String reason) {
        DevGramPlugins.reportReview(entry.id, reviewUserId, reason,
                ok -> BulletinFactory.of(this).createSimpleBulletin(R.raw.contact_check, ok ? "Жалоба отправлена" : "Не удалось отправить жалобу").show());
    }
}

/*
 * DevGram: панель модерации каталога плагинов. Доступна модераторам (Firebase-аккаунт,
 * UID в /moderators) и главному админу. Список заявок из plugins_pending: «Одобрить»
 * (перенос в каталог) / «Отклонить» (+ опц. блокировка файла). Главный админ управляет
 * модераторами (создать аккаунт + записать в /moderators / удалить).
 */

package org.telegram.ui;

import android.content.Context;
import android.text.InputType;
import android.text.method.PasswordTransformationMethod;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.DevGramBadges;
import org.telegram.messenger.DevGramPlugins;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.messenger.browser.Browser;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.BulletinFactory;
import org.telegram.ui.Components.EditTextBoldCursor;
import org.telegram.ui.Components.LayoutHelper;

import java.util.ArrayList;

import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import org.telegram.ui.Components.RecyclerListView;

public class DevGramModerationActivity extends BaseFragment {

    private RecyclerListView listView;
    private Adapter adapter;
    private final ArrayList<DevGramPlugins.CatalogEntry> pending = new ArrayList<>();
    private boolean loading = true;
    private TextView emptyView;

    private static final int MENU_OVERFLOW = 10;
    private static final int MENU_MODERATORS = 1;
    private static final int MENU_NOTIFY = 2;
    private static final int MENU_REPORTS = 3;
    private static final int MENU_PLUGIN_REPORTS = 4;

    @Override
    public View createView(Context context) {
        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setAllowOverlayTitle(true);
        actionBar.setTitle("Модерация");
        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(int id) {
                if (id == -1) {
                    finishFragment();
                } else if (id == MENU_MODERATORS) {
                    openModerators();
                } else if (id == MENU_NOTIFY) {
                    boolean now = !DevGramPlugins.getModNotify();
                    DevGramPlugins.setModNotify(now);
                    BulletinFactory.of(DevGramModerationActivity.this)
                            .createSimpleBulletin(R.raw.contact_check,
                                    now ? "Уведомления о модерации включены" : "Уведомления о модерации выключены").show();
                } else if (id == MENU_REPORTS) {
                    ensureAdmin(() -> presentFragment(new DevGramReviewReportsActivity()));
                } else if (id == MENU_PLUGIN_REPORTS) {
                    ensureAdmin(() -> presentFragment(new DevGramPluginReportsActivity()));
                }
            }
        });
        org.telegram.ui.ActionBar.ActionBarMenuItem more =
                actionBar.createMenu().addItem(MENU_OVERFLOW, R.drawable.ic_ab_other);
        more.addSubItem(MENU_NOTIFY, R.drawable.msg_mute, "Уведомления о модерации");
        more.addSubItem(MENU_REPORTS, R.drawable.msg_report, "Жалобы на отзывы");
        more.addSubItem(MENU_PLUGIN_REPORTS, R.drawable.msg_report, "Жалобы на плагины");
        // управление модераторами — только главному админу
        if (DevGramBadges.isMainAdmin()) {
            more.addSubItem(MENU_MODERATORS, R.drawable.msg_groups, "Модераторы");
        }
        DevGramPlugins.loadModNotify();

        FrameLayout root = new FrameLayout(context);
        root.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundGray, resourceProvider));

        listView = new RecyclerListView(context, resourceProvider);
        listView.setLayoutManager(new LinearLayoutManager(context));
        listView.setVerticalScrollBarEnabled(false);
        listView.setPadding(0, AndroidUtilities.dp(6), 0, AndroidUtilities.dp(12));
        listView.setClipToPadding(false);
        adapter = new Adapter();
        listView.setAdapter(adapter);
        root.addView(listView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));

        emptyView = new TextView(context);
        emptyView.setGravity(Gravity.CENTER);
        emptyView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText, resourceProvider));
        emptyView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
        emptyView.setPadding(AndroidUtilities.dp(40), 0, AndroidUtilities.dp(40), 0);
        root.addView(emptyView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER));

        load();
        return fragmentView = root;
    }

    private void load() {
        loading = true;
        refreshEmpty();
        DevGramPlugins.fetchModerators(null); // обновить кэш модераторов
        DevGramPlugins.fetchPending(list -> {
            pending.clear();
            pending.addAll(list);
            loading = false;
            actionBar.setTitle("Модерация" + (pending.isEmpty() ? "" : " (" + pending.size() + ")"));
            if (adapter != null) adapter.notifyDataSetChanged();
            refreshEmpty();
        });
    }

    private void refreshEmpty() {
        if (emptyView == null) return;
        if (loading) {
            emptyView.setText("Загрузка заявок…");
            emptyView.setVisibility(View.VISIBLE);
        } else if (pending.isEmpty()) {
            emptyView.setText("Заявок на модерацию нет");
            emptyView.setVisibility(View.VISIBLE);
        } else {
            emptyView.setVisibility(View.GONE);
        }
    }

    // ---------- карточка заявки ----------
    private View createCard(Context context, DevGramPlugins.CatalogEntry e) {
        LinearLayout card = new LinearLayout(context);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackground(Theme.createRoundRectDrawable(AndroidUtilities.dp(16),
                Theme.getColor(Theme.key_windowBackgroundWhite, resourceProvider)));
        card.setPadding(AndroidUtilities.dp(16), AndroidUtilities.dp(14), AndroidUtilities.dp(16), AndroidUtilities.dp(14));

        TextView title = new TextView(context);
        title.setText(e.name + (e.version.isEmpty() ? "" : "  v" + e.version));
        title.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText, resourceProvider));
        title.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
        title.setTypeface(AndroidUtilities.bold());
        card.addView(title, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        TextView meta = new TextView(context);
        String who = e.submitterName.isEmpty() ? (e.submitterId == 0 ? "Автор не указан" : String.valueOf(e.submitterId)) : e.submitterName;
        meta.setText((e.update ? "Обновление · " : "Новая публикация · ") + "от " + who + (e.submittedAt > 0 ? "\n" + android.text.format.DateFormat.format("dd.MM.yyyy HH:mm", e.submittedAt) : ""));
        meta.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText, resourceProvider));
        meta.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 12);
        card.addView(meta, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 4, 0, 0));

        StringBuilder sub = new StringBuilder();
        if (!e.author.isEmpty()) sub.append(e.author);
        if (!e.channel.isEmpty()) sub.append(sub.length() > 0 ? "  ·  " : "").append(e.channel);
        if (!e.filter.isEmpty()) sub.append(sub.length() > 0 ? "  ·  " : "").append(e.filter);
        if (sub.length() > 0) {
            TextView subtitle = new TextView(context);
            subtitle.setText(sub.toString());
            subtitle.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText, resourceProvider));
            subtitle.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
            if (!e.channel.isEmpty()) {
                subtitle.setOnClickListener(v -> openChannel(e.channel));
            }
            card.addView(subtitle, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 2, 0, 0));
        }

        if (!e.desc.isEmpty()) {
            TextView desc = new TextView(context);
            desc.setText(e.desc);
            desc.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText, resourceProvider));
            desc.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
            card.addView(desc, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 8, 0, 0));
        }

        // просмотреть исходник
        TextView view = new TextView(context);
        view.setText("Посмотреть код плагина");
        view.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlueText, resourceProvider));
        view.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
        view.setPadding(0, AndroidUtilities.dp(8), 0, AndroidUtilities.dp(4));
        view.setOnClickListener(v -> showSource(e));
        card.addView(view, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT));

        if (e.update) {
            TextView compare = new TextView(context);
            compare.setText("Сравнить с опубликованной версией");
            compare.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlueText, resourceProvider));
            compare.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
            compare.setPadding(0, AndroidUtilities.dp(5), 0, AndroidUtilities.dp(5));
            compare.setOnClickListener(v -> DevGramPlugins.fetchCatalog(entries -> {
                for (DevGramPlugins.CatalogEntry old : entries) if (old.id.equals(e.id)) { showDiff(old, e); return; }
                BulletinFactory.of(this).createErrorBulletin("Опубликованная версия не найдена").show();
            }));
            card.addView(compare, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT));
        }

        TextView history = new TextView(context);
        history.setText("История публикации");
        history.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlueText, resourceProvider));
        history.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
        history.setPadding(0, AndroidUtilities.dp(5), 0, AndroidUtilities.dp(5));
        history.setOnClickListener(v -> presentFragment(new DevGramPluginHistoryActivity(e.id, e.name)));
        card.addView(history, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT));

        // кнопки: Одобрить / Отклонить
        LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.HORIZONTAL);

        TextView approve = pillButton(context, "Одобрить",
                Theme.getColor(Theme.key_featuredStickers_buttonText, resourceProvider),
                Theme.getColor(Theme.key_featuredStickers_addButton, resourceProvider));
        approve.setOnClickListener(v -> ensureAdmin(() -> {
            if (DevGramPlugins.approvePending(e)) {
                pending.remove(e);
                afterAction("Одобрено: " + e.name);
            }
        }));
        row.addView(approve, LayoutHelper.createLinear(0, LayoutHelper.WRAP_CONTENT, 1f, 0, 0, 8, 0));

        TextView reject = pillButton(context, "Отклонить",
                Theme.getColor(Theme.key_text_RedRegular, resourceProvider),
                Theme.multAlpha(Theme.getColor(Theme.key_text_RedRegular, resourceProvider), 0.12f));
        reject.setOnClickListener(v -> confirmReject(e));
        row.addView(reject, LayoutHelper.createLinear(0, LayoutHelper.WRAP_CONTENT, 1f));

        card.addView(row, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 10, 0, 0));

        LinearLayout outer = new LinearLayout(context);
        outer.setPadding(AndroidUtilities.dp(12), AndroidUtilities.dp(5), AndroidUtilities.dp(12), AndroidUtilities.dp(5));
        outer.addView(card, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
        return outer;
    }

    private TextView pillButton(Context ctx, String text, int textColor, int bg) {
        TextView t = new TextView(ctx);
        t.setText(text);
        t.setGravity(Gravity.CENTER);
        t.setTextColor(textColor);
        t.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        t.setTypeface(AndroidUtilities.bold());
        t.setPadding(0, AndroidUtilities.dp(10), 0, AndroidUtilities.dp(10));
        t.setBackground(Theme.createRoundRectDrawable(AndroidUtilities.dp(12), bg));
        return t;
    }

    private void afterAction(String msg) {
        actionBar.setTitle("Модерация" + (pending.isEmpty() ? "" : " (" + pending.size() + ")"));
        if (adapter != null) adapter.notifyDataSetChanged();
        refreshEmpty();
        BulletinFactory.of(this).createSimpleBulletin(R.raw.contact_check, msg).show();
    }

    private void confirmReject(DevGramPlugins.CatalogEntry e) {
        final EditTextBoldCursor reason = new EditTextBoldCursor(getParentActivity());
        reason.setHint("Причина отказа");
        reason.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 17);
        reason.setTextColor(Theme.getColor(Theme.key_dialogTextBlack, resourceProvider));
        reason.setHintTextColor(Theme.getColor(Theme.key_groupcreate_hintText, resourceProvider));
        reason.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        reason.setMinLines(3);
        AlertDialog.Builder b = new AlertDialog.Builder(getParentActivity());
        b.setTitle("Отклонить заявку");
        b.setMessage("Укажите автору, что нужно исправить в «" + e.name + "».");
        b.setView(reason);
        b.setItems(new CharSequence[]{"Отклонить", "Отклонить и заблокировать файл"}, (d, which) -> {
            final boolean block = which == 1;
            final String text = reason.getText() == null ? "" : reason.getText().toString().trim();
            if (text.isEmpty()) {
                BulletinFactory.of(this).createErrorBulletin("Укажите причину отказа").show();
                return;
            }
            ensureAdmin(() -> {
                if (DevGramPlugins.rejectPending(e, block, text)) {
                    pending.remove(e);
                    afterAction(block ? "Отклонено и заблокировано" : "Отклонено");
                }
            });
        });
        b.setNegativeButton(LocaleController.getString(R.string.Cancel), null);
        showDialog(b.create());
    }

    private void showSource(DevGramPlugins.CatalogEntry e) {
        AlertDialog.Builder b = new AlertDialog.Builder(getParentActivity());
        b.setTitle(e.name + " — код");
        String src = e.source == null ? "" : e.source;
        b.setMessage(src.length() > 4000 ? src.substring(0, 4000) + "\n…" : src);
        b.setPositiveButton("Копировать", (d, w) -> {
            AndroidUtilities.addToClipboard(src);
            BulletinFactory.of(this).createCopyBulletin("Скопировано").show();
        });
        b.setNegativeButton("Закрыть", null);
        showDialog(b.create());
    }

    private void showDiff(DevGramPlugins.CatalogEntry oldEntry, DevGramPlugins.CatalogEntry newEntry) {
        String[] oldLines = (oldEntry.source == null ? "" : oldEntry.source).split("\\n", -1);
        String[] newLines = (newEntry.source == null ? "" : newEntry.source).split("\\n", -1);
        StringBuilder diff = new StringBuilder("Версия ").append(oldEntry.version).append(" → ").append(newEntry.version).append("\n\n");
        int max = Math.max(oldLines.length, newLines.length), shown = 0;
        for (int i = 0; i < max && shown < 120; i++) {
            String a = i < oldLines.length ? oldLines[i] : "", b = i < newLines.length ? newLines[i] : "";
            if (a.equals(b)) continue;
            if (!a.isEmpty()) diff.append("− ").append(a).append('\n');
            if (!b.isEmpty()) diff.append("+ ").append(b).append('\n');
            shown++;
        }
        if (shown == 0) diff.append("Изменений в исходнике нет."); else if (shown >= 120) diff.append("\n…показаны первые 120 изменений");
        new AlertDialog.Builder(getParentActivity()).setTitle("Изменения плагина").setMessage(diff.toString()).setPositiveButton("Закрыть", null).show();
    }

    private void openChannel(String channel) {
        String u = channel.trim();
        if (u.startsWith("@")) u = u.substring(1);
        if (u.startsWith("http")) Browser.openUrl(getContext(), u);
        else if (!u.isEmpty()) Browser.openUrl(getContext(), "https://t.me/" + u);
    }

    // ---------- управление модераторами (главный админ) ----------
    private void openModerators() {
        ensureAdmin(() -> DevGramPlugins.fetchModerators(list -> showModerators(list)));
    }

    private void showModerators(ArrayList<DevGramPlugins.Moderator> mods) {
        Context ctx = getParentActivity();
        if (ctx == null) return;
        ArrayList<CharSequence> items = new ArrayList<>();
        for (DevGramPlugins.Moderator m : mods) {
            String label = (m.email == null || m.email.isEmpty() ? m.uid : m.email);
            if (m.tg != 0) label += "  ·  id " + m.tg;
            items.add(label);
        }
        items.add("＋ Добавить модератора");
        AlertDialog.Builder b = new AlertDialog.Builder(ctx);
        b.setTitle("Модераторы (" + mods.size() + ")");
        b.setItems(items.toArray(new CharSequence[0]), (d, which) -> {
            if (which == mods.size()) {
                addModeratorDialog();
            } else {
                final DevGramPlugins.Moderator m = mods.get(which);
                AlertDialog.Builder rb = new AlertDialog.Builder(getParentActivity());
                rb.setTitle("Убрать модератора");
                rb.setMessage("Убрать доступ у «" + (m.email == null || m.email.isEmpty() ? m.uid : m.email) + "»?");
                rb.setPositiveButton("Убрать", (dd, ww) -> {
                    DevGramPlugins.removeModerator(m.uid);
                    BulletinFactory.of(this).createSimpleBulletin(R.raw.contact_check, "Доступ убран").show();
                });
                rb.setNegativeButton(LocaleController.getString(R.string.Cancel), null);
                showDialog(rb.create());
            }
        });
        b.setNegativeButton("Закрыть", null);
        showDialog(b.create());
    }

    private void addModeratorDialog() {
        Context ctx = getParentActivity();
        EditTextBoldCursor emailEt = makeInput(ctx, "Email модератора", InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS | InputType.TYPE_CLASS_TEXT);
        EditTextBoldCursor passEt = makeInput(ctx, "Пароль (мин. 6 символов)", InputType.TYPE_TEXT_VARIATION_PASSWORD | InputType.TYPE_CLASS_TEXT);
        passEt.setTransformationMethod(PasswordTransformationMethod.getInstance());
        EditTextBoldCursor tgEt = makeInput(ctx, "Telegram ID модератора", InputType.TYPE_CLASS_NUMBER);
        LinearLayout box = new LinearLayout(ctx);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(AndroidUtilities.dp(24), AndroidUtilities.dp(4), AndroidUtilities.dp(24), 0);
        box.addView(emailEt, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 44));
        box.addView(passEt, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 44, 0f, 12f, 0f, 0f));
        box.addView(tgEt, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 44, 0f, 12f, 0f, 0f));
        TextView hint = new TextView(ctx);
        hint.setText("Telegram ID нужен, чтобы кнопка модерации показалась именно этому человеку. ID можно узнать через @getmyid_bot.");
        hint.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 12);
        hint.setTextColor(Theme.getColor(Theme.key_dialogTextGray3, resourceProvider));
        box.addView(hint, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0f, 8f, 0f, 0f));
        AlertDialog.Builder b = new AlertDialog.Builder(ctx);
        b.setTitle("Новый модератор");
        b.setView(box);
        b.setPositiveButton("Создать", (d, w) -> {
            String email = emailEt.getText().toString().trim();
            String pass = passEt.getText().toString();
            long tg = org.telegram.messenger.Utilities.parseLong(tgEt.getText().toString());
            if (email.isEmpty() || pass.length() < 6) {
                Toast.makeText(getParentActivity(), "Введите email и пароль от 6 символов", Toast.LENGTH_LONG).show();
                return;
            }
            if (tg <= 0) {
                Toast.makeText(getParentActivity(), "Введите Telegram ID модератора", Toast.LENGTH_LONG).show();
                return;
            }
            DevGramPlugins.addModerator(email, pass, tg, (ok, err) ->
                    BulletinFactory.of(this).createSimpleBulletin(R.raw.contact_check,
                            ok ? "Модератор добавлен. Логин: " + email : ("Ошибка: " + err)).show());
        });
        b.setNegativeButton(LocaleController.getString(R.string.Cancel), null);
        showDialog(b.create());
        emailEt.requestFocus();
        AndroidUtilities.showKeyboard(emailEt);
    }

    // ---------- вход (email/пароль) ----------
    private void ensureAdmin(Runnable onReady) {
        if (DevGramBadges.isSignedIn()) {
            onReady.run();
            return;
        }
        Context ctx = getParentActivity();
        if (ctx == null) return;
        EditTextBoldCursor emailEt = makeInput(ctx, "Email", InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS | InputType.TYPE_CLASS_TEXT);
        EditTextBoldCursor passEt = makeInput(ctx, "Пароль", InputType.TYPE_TEXT_VARIATION_PASSWORD | InputType.TYPE_CLASS_TEXT);
        passEt.setTransformationMethod(PasswordTransformationMethod.getInstance());
        LinearLayout box = new LinearLayout(ctx);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(AndroidUtilities.dp(24), AndroidUtilities.dp(4), AndroidUtilities.dp(24), 0);
        box.addView(emailEt, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 44));
        box.addView(passEt, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 44, 0f, 12f, 0f, 0f));
        AlertDialog.Builder b = new AlertDialog.Builder(ctx);
        b.setTitle("Вход модератора");
        b.setView(box);
        b.setPositiveButton("Войти", (d, w) -> {
            String email = emailEt.getText().toString().trim();
            String pass = passEt.getText().toString();
            if (email.isEmpty() || pass.isEmpty()) return;
            DevGramBadges.signIn(email, pass, (ok, err) -> {
                if (ok) {
                    DevGramPlugins.fetchModerators(m -> onReady.run());
                } else {
                    Toast.makeText(getParentActivity(), "Не удалось войти: " + err, Toast.LENGTH_LONG).show();
                }
            });
        });
        b.setNegativeButton(LocaleController.getString(R.string.Cancel), null);
        showDialog(b.create());
        emailEt.requestFocus();
        AndroidUtilities.showKeyboard(emailEt);
    }

    private EditTextBoldCursor makeInput(Context context, String hint, int inputType) {
        EditTextBoldCursor et = new EditTextBoldCursor(context);
        et.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 18);
        et.setTextColor(Theme.getColor(Theme.key_dialogTextBlack, resourceProvider));
        et.setInputType(inputType);
        et.setHint(hint);
        et.setHintTextColor(Theme.getColor(Theme.key_groupcreate_hintText, resourceProvider));
        et.setCursorColor(Theme.getColor(Theme.key_dialogTextBlack, resourceProvider));
        et.setCursorSize(AndroidUtilities.dp(20));
        et.setCursorWidth(1.5f);
        et.setBackgroundDrawable(Theme.createEditTextDrawable(context, true));
        return et;
    }

    private class Adapter extends RecyclerListView.SelectionAdapter {
        @Override
        public boolean isEnabled(RecyclerView.ViewHolder holder) {
            return false;
        }

        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(android.view.ViewGroup parent, int viewType) {
            FrameLayout holder = new FrameLayout(parent.getContext());
            holder.setLayoutParams(new RecyclerView.LayoutParams(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
            return new RecyclerListView.Holder(holder);
        }

        @Override
        public void onBindViewHolder(RecyclerView.ViewHolder h, int position) {
            FrameLayout holder = (FrameLayout) h.itemView;
            holder.removeAllViews();
            holder.addView(createCard(holder.getContext(), pending.get(position)),
                    LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
        }

        @Override
        public int getItemCount() {
            return pending.size();
        }
    }
}

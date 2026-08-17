/*
 * DevGram: Каталог плагинов. Плагины публикуют разработчики каналов со значком 🧩 (или команда),
 * выбирая фильтр-категорию. Команда управляет фильтрами и может удалить плагин с блокировкой файла.
 * Поиск, фильтры-чипы, аватарки, описание, канал, установка. Админ-действия требуют входа команды.
 */

package org.telegram.ui;

import android.content.Context;
import android.graphics.Typeface;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.text.method.PasswordTransformationMethod;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
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
import org.telegram.ui.Components.RadialProgressView;
import org.telegram.ui.Components.RecyclerListView;

import java.util.ArrayList;

import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

public class DevGramPluginCatalogActivity extends BaseFragment {

    private RecyclerListView listView;
    private Adapter adapter;
    private final ArrayList<DevGramPlugins.CatalogEntry> all = new ArrayList<>();
    private final ArrayList<DevGramPlugins.CatalogEntry> shown = new ArrayList<>();
    private final ArrayList<String> filters = new ArrayList<>();
    private boolean loading = true;
    private boolean team;
    private String query = "";
    private String activeFilter = "";
    private LinearLayout chipRow;
    private TextView catalogSummary;
    private TextView emptyView;
    private org.telegram.ui.ActionBar.ActionBarMenuItem moderationItem;
    private int sortMode;
    private int loadGeneration;
    private boolean destroyed;
    private RadialProgressView progressView;

    @Override
    public View createView(Context context) {
        destroyed = false;
        team = DevGramBadges.isTeam(getUserConfig().getClientUserId());

        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setAllowOverlayTitle(true);
        actionBar.setTitle("Каталог плагинов");
        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(int id) {
                if (id == -1) {
                    finishFragment();
                } else if (id == 1) {
                    openModeration();
                } else if (id == 2) {
                    presentFragment(new DevGramMySubmissionsActivity());
                } else if (id >= 10 && id <= 12) {
                    sortMode = id - 10;
                    applyFilter();
                }
            }
        });
        // вход в панель модерации — скрыт по умолчанию, показываем только модераторам (по tg-id)
        moderationItem = actionBar.createMenu().addItem(1, R.drawable.msg_shareout);
        moderationItem.setVisibility(View.GONE);
        org.telegram.ui.ActionBar.ActionBarMenuItem more = actionBar.createMenu().addItem(3, R.drawable.ic_ab_other);
        more.addSubItem(2, R.drawable.msg_info, "Мои заявки");
        more.addSubItem(10, R.drawable.msg_recent, "Сначала новые");
        more.addSubItem(11, R.drawable.msg_info, "По рейтингу");
        more.addSubItem(12, R.drawable.msg_discussion, "По отзывам");

        LinearLayout container = new LinearLayout(context);
        container.setOrientation(LinearLayout.VERTICAL);
        container.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundGray, resourceProvider));

        // --- поиск (в «пилюле») ---
        FrameLayout searchWrap = new FrameLayout(context);
        searchWrap.setBackground(Theme.createRoundRectDrawable(AndroidUtilities.dp(21),
                Theme.getColor(Theme.key_windowBackgroundWhite, resourceProvider)));
        ImageView searchIcon = new ImageView(context);
        searchIcon.setImageResource(R.drawable.msg_search);
        searchIcon.setColorFilter(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText, resourceProvider));
        searchWrap.addView(searchIcon, LayoutHelper.createFrame(20, 20, Gravity.CENTER_VERTICAL | Gravity.LEFT, 14, 0, 0, 0));
        EditText search = new EditText(context);
        search.setHint("Поиск плагинов");
        search.setSingleLine(true);
        search.setBackground(null);
        search.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
        search.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText, resourceProvider));
        search.setHintTextColor(Theme.getColor(Theme.key_groupcreate_hintText, resourceProvider));
        search.setPadding(AndroidUtilities.dp(44), 0, AndroidUtilities.dp(14), 0);
        search.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
            public void onTextChanged(CharSequence s, int a, int b, int c) {}
            public void afterTextChanged(Editable s) {
                query = s.toString().trim().toLowerCase();
                applyFilter();
            }
        });
        searchWrap.addView(search, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 42));
        container.addView(searchWrap, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 42, 12, 12, 12, 8));

        // --- категории каталога ---
        LinearLayout categoryHeader = new LinearLayout(context);
        categoryHeader.setOrientation(LinearLayout.HORIZONTAL);
        categoryHeader.setGravity(Gravity.CENTER_VERTICAL);
        TextView categoryTitle = new TextView(context);
        categoryTitle.setText("Категории");
        categoryTitle.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
        categoryTitle.setTypeface(AndroidUtilities.bold());
        categoryTitle.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText, resourceProvider));
        categoryHeader.addView(categoryTitle, LayoutHelper.createLinear(0, LayoutHelper.WRAP_CONTENT, 1f));
        catalogSummary = new TextView(context);
        catalogSummary.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
        catalogSummary.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText, resourceProvider));
        categoryHeader.addView(catalogSummary, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT));
        container.addView(categoryHeader, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 16, 3, 16, 8));

        HorizontalScrollView chipScroll = new HorizontalScrollView(context);
        chipScroll.setHorizontalScrollBarEnabled(false);
        chipScroll.setClipToPadding(false);
        chipRow = new LinearLayout(context);
        chipRow.setOrientation(LinearLayout.HORIZONTAL);
        chipRow.setPadding(AndroidUtilities.dp(12), 0, AndroidUtilities.dp(12), 0);
        chipScroll.addView(chipRow, new FrameLayout.LayoutParams(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT));
        container.addView(chipScroll, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 0, 0, 8));
        rebuildChips();

        // --- список карточек ---
        FrameLayout listWrap = new FrameLayout(context);
        listView = new RecyclerListView(context, resourceProvider);
        listView.setLayoutManager(new LinearLayoutManager(context));
        listView.setVerticalScrollBarEnabled(false);
        listView.setClipToPadding(false);
        listView.setPadding(0, 0, 0, AndroidUtilities.dp(12));
        adapter = new Adapter();
        listView.setAdapter(adapter);
        listWrap.addView(listView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));

        emptyView = new TextView(context);
        emptyView.setGravity(Gravity.CENTER);
        emptyView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText, resourceProvider));
        emptyView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
        emptyView.setPadding(AndroidUtilities.dp(40), 0, AndroidUtilities.dp(40), 0);
        emptyView.setVisibility(View.GONE);
        listWrap.addView(emptyView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER));

        // DevGram: крутящийся индикатор загрузки каталога — раньше при входе был только
        // текст «Загрузка каталога…» без какой-либо анимации.
        progressView = new RadialProgressView(context, resourceProvider);
        progressView.setSize(AndroidUtilities.dp(28));
        progressView.setVisibility(View.GONE);
        listWrap.addView(progressView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER));

        container.addView(listWrap, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 0, 1f));

        loadAll();
        return fragmentView = container;
    }

    @Override
    public void onResume() {
        super.onResume();
        if (fragmentView != null && !destroyed) loadAll();
    }

    // ---------- чипы ----------
    private void rebuildChips() {
        if (destroyed || chipRow == null || !chipRow.isAttachedToWindow() && fragmentView != null) return;
        Context context = chipRow.getContext();
        if (context == null) return;
        chipRow.removeAllViews();
        ArrayList<String> names = new ArrayList<>();
        names.add("");
        names.addAll(filters);
        for (String name : names) {
            final String f = name;
            boolean on = activeFilter.equals(f);
            TextView chip = new TextView(context);
            int count = countForFilter(f);
            chip.setText((name.isEmpty() ? "Все" : name) + "  " + count);
            chip.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
            chip.setTypeface(on ? AndroidUtilities.bold() : Typeface.DEFAULT);
            chip.setGravity(Gravity.CENTER);
            chip.setPadding(AndroidUtilities.dp(16), AndroidUtilities.dp(10), AndroidUtilities.dp(16), AndroidUtilities.dp(10));
            chip.setTextColor(on ? Theme.getColor(Theme.key_featuredStickers_buttonText, resourceProvider)
                    : Theme.getColor(Theme.key_windowBackgroundWhiteBlackText, resourceProvider));
            chip.setBackground(Theme.createSimpleSelectorRoundRectDrawable(AndroidUtilities.dp(20),
                    on ? Theme.getColor(Theme.key_featuredStickers_addButton, resourceProvider)
                            : Theme.getColor(Theme.key_windowBackgroundWhite, resourceProvider),
                    Theme.getColor(Theme.key_listSelector, resourceProvider)));
            chip.setOnClickListener(v -> { activeFilter = f; rebuildChips(); applyFilter(); });
            if (team && !f.isEmpty()) {
                chip.setOnLongClickListener(v -> { showFilterActions(f); return true; });
            }
            chipRow.addView(chip, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, 0, 0, 8, 0));
        }
        if (team) {
            TextView add = new TextView(context);
            add.setText("＋  Категория");
            add.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
            add.setTypeface(AndroidUtilities.bold());
            add.setGravity(Gravity.CENTER);
            add.setPadding(AndroidUtilities.dp(16), AndroidUtilities.dp(10), AndroidUtilities.dp(16), AndroidUtilities.dp(10));
            add.setTextColor(Theme.getColor(Theme.key_featuredStickers_addButton, resourceProvider));
            add.setBackground(Theme.createRoundRectDrawable(AndroidUtilities.dp(17),
                    Theme.getColor(Theme.key_windowBackgroundWhite, resourceProvider)));
            add.setOnClickListener(v -> addFilterDialog());
            chipRow.addView(add, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, 0, 0, 8, 0));
        }
        if (catalogSummary != null) {
            catalogSummary.setText(shown.size() + " из " + all.size());
        }
    }

    private int countForFilter(String filter) {
        if (filter == null || filter.isEmpty()) return all.size();
        int count = 0;
        for (DevGramPlugins.CatalogEntry entry : all) {
            if (filter.equals(entry.filter)) count++;
        }
        return count;
    }

    // ---------- данные ----------
    private void loadAll() {
        final int generation = ++loadGeneration;
        loading = true;
        DevGramPlugins.fetchFilters(f -> {
            if (!isLoadActive(generation)) return;
            filters.clear();
            filters.addAll(f);
            rebuildChips();
        });
        DevGramPlugins.fetchCatalogFast(entries -> {
            if (!isLoadActive(generation)) return;
            all.clear();
            all.addAll(entries);
            loading = false;
            applyFilter();
            // Фильтры и каталог загружаются параллельно. Категории могли отрисоваться раньше,
            // когда список all был ещё пустым, поэтому обновляем их счётчики после каталога.
            rebuildChips();
        }, entries -> {
            if (!isLoadActive(generation)) return;
            all.clear();
            all.addAll(entries);
            applyFilter();
            rebuildChips();
        });
        // показать вход в модерацию только модераторам (по их Telegram-ID)
        DevGramPlugins.fetchModerators(m -> {
            if (isLoadActive(generation) && moderationItem != null) {
                long myTg = getUserConfig().getClientUserId();
                moderationItem.setVisibility(DevGramPlugins.canSeeModeration(myTg) ? View.VISIBLE : View.GONE);
            }
        });
    }

    private boolean isLoadActive(int generation) {
        return !destroyed && generation == loadGeneration && fragmentView != null && getParentActivity() != null;
    }

    @Override
    public void onFragmentDestroy() {
        destroyed = true;
        loadGeneration++;
        chipRow = null;
        catalogSummary = null;
        emptyView = null;
        moderationItem = null;
        adapter = null;
        listView = null;
        super.onFragmentDestroy();
    }

    private boolean matches(DevGramPlugins.CatalogEntry e) {
        if (!activeFilter.isEmpty() && !activeFilter.equals(e.filter)) return false;
        if (query.isEmpty()) return true;
        return (e.name + " " + e.desc + " " + e.author + " " + e.channel).toLowerCase().contains(query);
    }

    private void applyFilter() {
        shown.clear();
        for (DevGramPlugins.CatalogEntry e : all) {
            if (matches(e)) shown.add(e);
        }
        if (sortMode == 1) shown.sort((a, b) -> Double.compare(b.rating, a.rating));
        else if (sortMode == 2) shown.sort((a, b) -> Integer.compare(b.reviews, a.reviews));
        else shown.sort((a, b) -> Long.compare(b.updatedAt, a.updatedAt));
        if (adapter != null) adapter.notifyDataSetChanged();
        if (catalogSummary != null) catalogSummary.setText(shown.size() + " из " + all.size());
        if (progressView != null) {
            progressView.setVisibility(loading ? View.VISIBLE : View.GONE);
        }
        if (emptyView != null) {
            if (loading) {
                emptyView.setVisibility(View.GONE);
            } else if (shown.isEmpty()) {
                emptyView.setText(all.isEmpty()
                        ? "В каталоге пока нет плагинов.\nРазработчики публикуют их из каналов со значком 🧩."
                        : "Ничего не найдено");
                emptyView.setVisibility(View.VISIBLE);
            } else {
                emptyView.setVisibility(View.GONE);
            }
        }
    }

    // ---------- карточка плагина ----------
    private View createCard(Context context, DevGramPlugins.CatalogEntry e) {
        LinearLayout card = new LinearLayout(context);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackground(Theme.createSimpleSelectorRoundRectDrawable(AndroidUtilities.dp(20),
                Theme.getColor(Theme.key_windowBackgroundWhite, resourceProvider),
                Theme.getColor(Theme.key_listSelector, resourceProvider)));
        card.setPadding(AndroidUtilities.dp(15), AndroidUtilities.dp(13), AndroidUtilities.dp(13), AndroidUtilities.dp(13));
        card.setOnClickListener(v -> presentFragment(new DevGramPluginDetailsActivity(e)));

        // шапка
        LinearLayout head = new LinearLayout(context);
        head.setOrientation(LinearLayout.HORIZONTAL);
        head.setGravity(Gravity.TOP);

        ImageView icon = new ImageView(context);
        icon.setImageResource(R.drawable.devgram_plugins);
        icon.setColorFilter(Theme.getColor(Theme.key_featuredStickers_addButton, resourceProvider));
        icon.setBackground(Theme.createRoundRectDrawable(AndroidUtilities.dp(16), Theme.getColor(Theme.key_featuredStickers_addButton, resourceProvider)));
        icon.setColorFilter(Theme.getColor(Theme.key_featuredStickers_buttonText, resourceProvider));
        icon.setPadding(AndroidUtilities.dp(13), AndroidUtilities.dp(13), AndroidUtilities.dp(13), AndroidUtilities.dp(13));
        icon.setClipToOutline(true);
        if (e.icon != null && !e.icon.isEmpty()) loadIcon(icon, e.icon);
        head.addView(icon, LayoutHelper.createLinear(56, 56, Gravity.TOP, 0, 2, 0, 0));

        LinearLayout titleCol = new LinearLayout(context);
        titleCol.setOrientation(LinearLayout.VERTICAL);
        TextView title = new TextView(context);
        title.setText(e.name);
        title.setSingleLine(true);
        title.setEllipsize(android.text.TextUtils.TruncateAt.END);
        title.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText, resourceProvider));
        title.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 17);
        title.setTypeface(AndroidUtilities.bold());
        titleCol.addView(title, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
        StringBuilder sub = new StringBuilder();
        if (!e.version.isEmpty()) sub.append("v").append(e.version);
        if (!e.author.isEmpty()) sub.append(sub.length() > 0 ? "  •  " : "").append(e.author);
        if (sub.length() > 0) {
            TextView subtitle = new TextView(context);
            subtitle.setText(sub.toString());
            subtitle.setSingleLine(true);
            subtitle.setEllipsize(android.text.TextUtils.TruncateAt.END);
            subtitle.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText, resourceProvider));
            subtitle.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 12);
            titleCol.addView(subtitle, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 2, 0, 0));
        }
        LinearLayout micro = new LinearLayout(context); micro.setGravity(Gravity.CENTER_VERTICAL);
        if (e.rating > 0) { TextView rating = pill(context, String.format(java.util.Locale.US, "★ %.1f", e.rating), true); micro.addView(rating, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, 0, 7, 5, 0)); }
        if (!e.filter.isEmpty()) micro.addView(pill(context, e.filter, false), LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, 0, 7, 5, 0));
        if (micro.getChildCount() > 0) titleCol.addView(micro);
        head.addView(titleCol, LayoutHelper.createLinear(0, LayoutHelper.WRAP_CONTENT, 1f, Gravity.CENTER_VERTICAL, 12, 0, 6, 0));

        if (team) {
            ImageView del = new ImageView(context);
            del.setImageResource(R.drawable.msg_delete);
            del.setColorFilter(Theme.getColor(Theme.key_text_RedRegular));
            del.setScaleType(ImageView.ScaleType.CENTER);
            del.setBackground(Theme.createSelectorDrawable(Theme.getColor(Theme.key_listSelector), Theme.RIPPLE_MASK_CIRCLE_20DP));
            del.setOnClickListener(v -> confirmDelete(e));
            head.addView(del, LayoutHelper.createLinear(36, 36, Gravity.CENTER_VERTICAL));
        }
        card.addView(head, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        if (!e.desc.isEmpty()) {
            TextView desc = new TextView(context);
            desc.setText(e.desc);
            desc.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText, resourceProvider));
            desc.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
            desc.setMaxLines(2);
            desc.setEllipsize(android.text.TextUtils.TruncateAt.END);
            desc.setLineSpacing(AndroidUtilities.dp(2), 1f);
            card.addView(desc, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 11, 0, 0));
        }

        // низ: чипы (канал 🧩 + фильтр) слева, кнопка справа
        LinearLayout bottom = new LinearLayout(context);
        bottom.setOrientation(LinearLayout.HORIZONTAL);
        bottom.setGravity(Gravity.CENTER_VERTICAL);

        LinearLayout tags = new LinearLayout(context);
        tags.setOrientation(LinearLayout.HORIZONTAL);
        tags.setGravity(Gravity.CENTER_VERTICAL);
        bottom.addView(tags, LayoutHelper.createLinear(0, LayoutHelper.WRAP_CONTENT, 1f, Gravity.CENTER_VERTICAL, 0, 0, 6, 0));

        if (!e.channel.isEmpty()) {
            TextView ch = pill(context, "🧩 " + e.channel, true);
            ch.setOnClickListener(v -> openChannel(e.channel));
            tags.addView(ch, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, 0, 0, 6, 0));
        }

        final boolean installed = DevGramPlugins.isInstalled(e.id);
        TextView btn = new TextView(context);
        btn.setText(installed ? "Обновить" : "Установить");
        btn.setTextColor(Theme.getColor(Theme.key_featuredStickers_buttonText, resourceProvider));
        btn.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
        btn.setTypeface(AndroidUtilities.bold());
        btn.setGravity(Gravity.CENTER);
        btn.setPadding(AndroidUtilities.dp(14), AndroidUtilities.dp(9), AndroidUtilities.dp(14), AndroidUtilities.dp(9));
        btn.setMinWidth(0);
        btn.setBackground(Theme.createSimpleSelectorRoundRectDrawable(AndroidUtilities.dp(18),
                Theme.getColor(Theme.key_featuredStickers_addButton, resourceProvider),
                Theme.getColor(Theme.key_featuredStickers_addButtonPressed, resourceProvider)));
        btn.setOnClickListener(v -> install(e));
        bottom.addView(btn, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT));
        card.addView(bottom, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 11, 0, 0));

        LinearLayout outer = new LinearLayout(context);
        outer.setPadding(AndroidUtilities.dp(12), AndroidUtilities.dp(4), AndroidUtilities.dp(12), AndroidUtilities.dp(4));
        outer.addView(card, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
        return outer;
    }

    private TextView pill(Context ctx, String text, boolean accent) {
        TextView t = new TextView(ctx);
        t.setText(text);
        t.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 11);
        t.setSingleLine(true);
        t.setEllipsize(android.text.TextUtils.TruncateAt.END);
        int col = accent ? Theme.getColor(Theme.key_featuredStickers_addButton, resourceProvider)
                : Theme.getColor(Theme.key_windowBackgroundWhiteGrayText, resourceProvider);
        t.setTextColor(col);
        t.setPadding(AndroidUtilities.dp(7), AndroidUtilities.dp(3), AndroidUtilities.dp(7), AndroidUtilities.dp(3));
        t.setBackground(Theme.createRoundRectDrawable(AndroidUtilities.dp(7),
                Theme.multAlpha(col, 0.12f)));
        return t;
    }

    // ---------- действия ----------
    private void install(DevGramPlugins.CatalogEntry e) {
        if (e.isPackage) {
            // .dgplugin-пакет: качаем бинарь из архивного канала и ставим
            if (e.packageMsg == 0) {
                BulletinFactory.of(this).createErrorBulletin("Пакет ещё не размещён в архиве").show();
                return;
            }
            BulletinFactory.of(this).createSimpleBulletin(R.raw.info, "Скачиваю пакет «" + e.name + "»…").show();
            org.telegram.messenger.DevGramPackages.installCatalogPackage(e, ok ->
                    BulletinFactory.of(this).createSimpleBulletin(ok ? R.raw.contact_check : R.raw.error,
                            ok ? "Плагин установлен: " + e.name : "Не удалось установить пакет").show());
            if (adapter != null) adapter.notifyDataSetChanged();
            return;
        }
        if (e.source == null || e.source.isEmpty()) {
            BulletinFactory.of(this).createErrorBulletin("У плагина нет исходника в каталоге").show();
            return;
        }
        DevGramPlugins.trustFromChannel(e.source);
        boolean ok = DevGramPlugins.install(e.source, e.id, true);
        BulletinFactory.of(this).createSimpleBulletin(R.raw.contact_check,
                ok ? "Плагин установлен: " + e.name : "Не удалось установить").show();
        if (adapter != null) adapter.notifyDataSetChanged();
    }

    private void confirmDelete(DevGramPlugins.CatalogEntry e) {
        EditTextBoldCursor reason = makeInput(getParentActivity(), "Причина удаления", InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        AlertDialog.Builder b = new AlertDialog.Builder(getParentActivity());
        b.setTitle("Удалить из каталога");
        b.setMessage("Выберите обычное удаление или удаление с блокировкой файла. При обычном удалении плагин можно будет опубликовать снова.");
        b.setView(reason);
        b.setNeutralButton("Удалить", (d, w) -> ensureAdmin(() -> {
            String why=reason.getText().toString().trim();if(why.isEmpty()){BulletinFactory.of(this).createErrorBulletin("Укажите причину удаления").show();return;}
            if (DevGramPlugins.catalogDelete(e,why)) {
                all.remove(e);
                applyFilter();
                BulletinFactory.of(this).createSimpleBulletin(R.raw.contact_check, "Удалено из каталога").show();
            }
        }));
        b.setPositiveButton("Удалить и заблокировать", (d, w) -> ensureAdmin(() -> {
            String why=reason.getText().toString().trim();if(why.isEmpty()){BulletinFactory.of(this).createErrorBulletin("Укажите причину удаления").show();return;}
            if (DevGramPlugins.catalogDeleteAndBlock(e,why)) {
                all.remove(e);
                applyFilter();
                BulletinFactory.of(this).createSimpleBulletin(R.raw.contact_check, "Удалено и заблокировано").show();
            }
        }));
        b.setNegativeButton(LocaleController.getString(R.string.Cancel), null);
        showDialog(b.create());
    }

    private void addFilterDialog() {
        Context context = getParentActivity();
        EditTextBoldCursor et = makeInput(context, "Название фильтра", InputType.TYPE_CLASS_TEXT);
        FrameLayout box = new FrameLayout(context);
        box.setPadding(AndroidUtilities.dp(24), AndroidUtilities.dp(4), AndroidUtilities.dp(24), 0);
        box.addView(et, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 44));

        AlertDialog.Builder b = new AlertDialog.Builder(context);
        b.setTitle("Новый фильтр");
        b.setView(box);
        b.setPositiveButton("Добавить", (d, w) -> {
            String name = et.getText().toString().trim();
            if (name.isEmpty()) return;
            ensureAdmin(() -> {
                ArrayList<String> updated = new ArrayList<>(filters);
                if (!updated.contains(name)) updated.add(name);
                DevGramPlugins.saveFilters(updated, ok -> {
                    if (ok) {
                        filters.clear();
                        filters.addAll(updated);
                        rebuildChips();
                        BulletinFactory.of(this).createSimpleBulletin(R.raw.contact_check, "Фильтр добавлен").show();
                    } else {
                        BulletinFactory.of(this).createErrorBulletin("Не удалось сохранить фильтр в каталоге").show();
                    }
                });
            });
        });
        b.setNegativeButton(LocaleController.getString(R.string.Cancel), null);
        showDialog(b.create());
        et.requestFocus();
        AndroidUtilities.showKeyboard(et);
    }

    private void confirmRemoveFilter(String name) {
        AlertDialog.Builder b = new AlertDialog.Builder(getParentActivity());
        b.setTitle("Удалить фильтр");
        b.setMessage("Убрать фильтр «" + name + "»? Плагины не удалятся, только категория.");
        b.setPositiveButton("Удалить", (d, w) -> ensureAdmin(() -> {
            ArrayList<String> updated = new ArrayList<>(filters);
            updated.remove(name);
            DevGramPlugins.saveFilters(updated, ok -> {
                if (ok) {
                    filters.clear();
                    filters.addAll(updated);
                    if (activeFilter.equals(name)) activeFilter = "";
                    rebuildChips();
                    applyFilter();
                    BulletinFactory.of(this).createSimpleBulletin(R.raw.contact_check, "Категория удалена").show();
                } else {
                    BulletinFactory.of(this).createErrorBulletin("Не удалось удалить категорию").show();
                }
            });
        }));
        b.setNegativeButton(LocaleController.getString(R.string.Cancel), null);
        showDialog(b.create());
    }

    private void showFilterActions(String name) {
        int index = filters.indexOf(name);
        if (index < 0) return;
        ArrayList<CharSequence> actions = new ArrayList<>();
        ArrayList<Integer> ids = new ArrayList<>();
        if (index > 0) {
            actions.add("Переместить левее");
            ids.add(-1);
        }
        if (index < filters.size() - 1) {
            actions.add("Переместить правее");
            ids.add(1);
        }
        actions.add("Удалить категорию");
        ids.add(0);
        AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
        builder.setTitle(name);
        builder.setMessage("Управление категорией каталога");
        builder.setItems(actions.toArray(new CharSequence[0]), (dialog, which) -> {
            int action = ids.get(which);
            if (action == 0) {
                confirmRemoveFilter(name);
            } else {
                moveFilter(name, action);
            }
        });
        builder.setNegativeButton(LocaleController.getString(R.string.Cancel), null);
        showDialog(builder.create());
    }

    private void moveFilter(String name, int direction) {
        int from = filters.indexOf(name);
        int to = from + direction;
        if (from < 0 || to < 0 || to >= filters.size()) return;
        ArrayList<String> reordered = new ArrayList<>(filters);
        java.util.Collections.swap(reordered, from, to);
        ensureAdmin(() -> DevGramPlugins.saveFilters(reordered, ok -> {
            if (ok) {
                filters.clear();
                filters.addAll(reordered);
                rebuildChips();
                BulletinFactory.of(this).createSimpleBulletin(R.raw.contact_check, "Порядок категорий сохранён").show();
            } else {
                BulletinFactory.of(this).createErrorBulletin("Не удалось изменить порядок категорий").show();
            }
        }));
    }

    private void openChannel(String channel) {
        String u = channel.trim();
        if (u.startsWith("@")) u = u.substring(1);
        if (u.startsWith("http")) Browser.openUrl(getContext(), u);
        else if (!u.isEmpty()) Browser.openUrl(getContext(), "https://t.me/" + u);
    }

    // Открыть панель модерации: вход → проверка прав (главный админ или модератор) → переход.
    private void openModeration() {
        ensureAdmin(() -> DevGramPlugins.fetchModerators(m -> {
            if (DevGramPlugins.isModerator()) {
                presentFragment(new DevGramModerationActivity());
            } else {
                BulletinFactory.of(this).createErrorBulletin("Нет доступа к модерации").show();
            }
        }));
    }

    // ---------- вход команды (для админ-действий) ----------
    private void ensureAdmin(Runnable onReady) {
        if (DevGramPlugins.canManageVerified()) {
            onReady.run();
            return;
        }
        Context context = getParentActivity();
        if (context == null) return;
        EditTextBoldCursor emailEt = makeInput(context, "Email команды", InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS | InputType.TYPE_CLASS_TEXT);
        EditTextBoldCursor passEt = makeInput(context, "Пароль", InputType.TYPE_TEXT_VARIATION_PASSWORD | InputType.TYPE_CLASS_TEXT);
        passEt.setTransformationMethod(PasswordTransformationMethod.getInstance());
        LinearLayout box = new LinearLayout(context);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(AndroidUtilities.dp(24), AndroidUtilities.dp(4), AndroidUtilities.dp(24), 0);
        box.addView(emailEt, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 44));
        box.addView(passEt, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 44, 0f, 12f, 0f, 0f));
        AlertDialog.Builder b = new AlertDialog.Builder(context);
        b.setTitle("Вход команды DevGram");
        b.setView(box);
        b.setPositiveButton("Войти", (d, w) -> {
            String email = emailEt.getText().toString().trim();
            String pass = passEt.getText().toString();
            if (email.isEmpty() || pass.isEmpty()) return;
            DevGramBadges.signIn(email, pass, (ok, err) -> {
                if (ok) onReady.run();
                else Toast.makeText(getParentActivity(), "Не удалось войти: " + err, Toast.LENGTH_LONG).show();
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

    // ---------- аватарка ----------
    private void loadIcon(final ImageView iv, final String url) {
        final Context ctx = getContext();
        if (ctx == null) return;
        java.io.File dir = new java.io.File(ctx.getCacheDir(), "devgram_icons");
        dir.mkdirs();
        final java.io.File cache = new java.io.File(dir, Integer.toHexString(url.hashCode()) + ".img");
        if (cache.exists() && cache.length() > 0) {
            android.graphics.Bitmap bmp = android.graphics.BitmapFactory.decodeFile(cache.getAbsolutePath());
            if (bmp != null) { applyIcon(iv, bmp); return; }
        }
        new Thread(() -> {
            try {
                java.net.HttpURLConnection c = (java.net.HttpURLConnection) new java.net.URL(url).openConnection();
                c.setConnectTimeout(10000);
                c.setReadTimeout(15000);
                c.setInstanceFollowRedirects(true);
                c.setRequestProperty("User-Agent", "DevGram");
                java.io.InputStream in = c.getInputStream();
                java.io.File tmp = new java.io.File(cache.getAbsolutePath() + ".tmp");
                java.io.FileOutputStream out = new java.io.FileOutputStream(tmp);
                byte[] buf = new byte[8192];
                int n;
                while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
                out.close();
                in.close();
                c.disconnect();
                tmp.renameTo(cache);
                final android.graphics.Bitmap bmp = android.graphics.BitmapFactory.decodeFile(cache.getAbsolutePath());
                if (bmp != null) AndroidUtilities.runOnUIThread(() -> applyIcon(iv, bmp));
            } catch (Throwable e) {
                org.telegram.messenger.FileLog.e(e);
            }
        }).start();
    }

    private void applyIcon(ImageView iv, android.graphics.Bitmap bmp) {
        iv.setColorFilter(null);
        iv.setPadding(0, 0, 0, 0);
        iv.setScaleType(ImageView.ScaleType.CENTER_CROP);
        iv.setImageBitmap(bmp);
    }

    // ---------- адаптер ----------
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
            holder.addView(createCard(holder.getContext(), shown.get(position)),
                    LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
        }

        @Override
        public int getItemCount() {
            return shown.size();
        }
    }
}

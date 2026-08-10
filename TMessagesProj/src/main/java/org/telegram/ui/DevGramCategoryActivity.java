/*
 * DevGram: экран одной категории настроек мода.
 * Один фрагмент на все категории — разделы отличаются только набором опций.
 */

package org.telegram.ui;

import android.content.Context;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;

import org.telegram.messenger.DevGramConfig;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.UItem;
import org.telegram.ui.Components.UniversalAdapter;
import org.telegram.ui.Components.UniversalRecyclerView;

import java.util.ArrayList;

public class DevGramCategoryActivity extends BaseFragment {

    public static final int CATEGORY_GENERAL = 0;
    public static final int CATEGORY_GHOST = 1;
    public static final int CATEGORY_SPY = 2;
    public static final int CATEGORY_APPEARANCE = 3;
    public static final int CATEGORY_CHATS = 4;

    // Режим призрака
    private static final int ID_GHOST_MASTER = 1;
    private static final int ID_GHOST_READ = 2;
    private static final int ID_GHOST_ONLINE = 3;
    private static final int ID_GHOST_TYPING = 4;
    // Слежка
    private static final int ID_SAVE_DELETED = 5;
    private static final int ID_SAVE_HISTORY = 6;
    private static final int ID_SAVE_MEDIA = 10;
    private static final int ID_SAVE_BOTS = 11;
    // Основные
    private static final int ID_SHOW_CONTACTS = 7;
    private static final int ID_DISABLE_ADS = 14;
    private static final int ID_LOCAL_PREMIUM = 15;
    private static final int ID_STREAKS = 16;
    private static final int ID_VPN = 17;
    private static final int ID_IOS_PROFILE = 18;
    private static final int ID_VPN_DIAG = 19;
    // Внешний вид
    private static final int ID_NUMBER_ROUNDING = 20;
    private static final int ID_AVATAR_SHAPE = 21; // форма аватара (перенос из скрытых Fork-настроек)
    private static final int ID_SQUARE_FAB = 25;   // квадратная кнопка (FAB)
    private static final int ID_GLASS_MENU = 26;   // стеклянное меню сообщения
    private static final int ID_HIDE_EMOJI_CATEGORIES = 27; // скрыть строку категорий в поиске эмодзи
    private static final int ID_SYSTEM_EMOJI = 30;
    private static final int ID_FORCE_SNOW = 31;
    private static final int ID_FOLDER_TABS_STYLE = 32;
    private static final int ID_STICKER_SIZE = 33;
    private static final int ID_CENTER_TITLE = 34;
    // перенос рабочих настроек из скрытого Fork в наше меню
    private static final int ID_HIDE_ALL_CHATS = 35;
    private static final int ID_MINI_AVATARS = 36;
    private static final int ID_HIDE_CONTACTS_DIALOGS = 37;
    private static final int ID_LAST_SEEN_DOTS = 38;
    private static final int ID_CHAT_BLUR = 39;

    // ExteraGram 12.9.0: avatarCorners по умолчанию 28dp.
    private static int avatarCornersInit() {
        float f = org.telegram.messenger.AndroidUtilities.avatarCornersF();
        if (f < 0) {
            return org.telegram.messenger.AndroidUtilities.avatarCornersType()
                    == org.telegram.messenger.AndroidUtilities.AVATAR_CORNERS_SQUARE ? 0 : 28;
        }
        return Math.max(0, Math.min(30, (int) f));
    }

    private static boolean gPref(String key, boolean def) {
        return org.telegram.messenger.MessagesController.getGlobalMainSettings().getBoolean(key, def);
    }

    private static void gToggle(String key, boolean def) {
        org.telegram.messenger.MessagesController.getGlobalMainSettings()
                .edit().putBoolean(key, !gPref(key, def)).apply();
    }

    private static int gInt(String key, int def) {
        return org.telegram.messenger.MessagesController.getGlobalMainSettings().getInt(key, def);
    }

    // MD3 (master + 5 подпунктов) и остальные настройки внешнего вида — pref-ключи dg_*.
    private static final int ID_SINGLE_CORNER = 40;
    private static final int ID_HIDE_STATUS = 41;
    private static final int ID_HIDE_STORIES = 42;
    private static final int ID_HIDE_FAB = 43;
    private static final int ID_HIDE_SEARCHBAR = 44;
    private static final int ID_TAB_COUNTER = 45;
    private static final int ID_SYSTEM_FONTS = 46;
    private static final int ID_GOOEY = 47;
    private static final int ID_CUSTOM_THEMES = 48;
    private static final int ID_SEPARATE_HEADERS = 49;
    private static final int ID_ACTIONBAR_TITLE = 59;
    private static final int ID_DIVIDER_STYLE = 60;
    private static final int ID_GLASS_OUTLINE = 61;

    // Варианты для строк-селекторов (как в exteraGram)
    private static final String[] ACTIONBAR_TITLE_OPTIONS = {"Название приложения", "Имя пользователя", "Имя", "Чаты"};
    private static final String[] DIVIDER_STYLE_OPTIONS = {"Скрыть", "Линия", "Сегменты"};
    // Порядок значений совпадает с enum ExteraGram: GLARE, SOLID, HIDDEN.
    private static final String[] GLASS_OUTLINE_OPTIONS = {"Блики", "Сплошная", "Скрыта"};

    // Показать меню выбора; пишет int-ключ, обновляет список
    private void showChoice(String title, String[] options, String key, int def) {
        if (getParentActivity() == null) return;
        org.telegram.ui.ActionBar.AlertDialog.Builder b = new org.telegram.ui.ActionBar.AlertDialog.Builder(getParentActivity());
        b.setTitle(title);
        int cur = gInt(key, def);
        CharSequence[] labels = new CharSequence[options.length];
        for (int i = 0; i < options.length; i++) {
            labels[i] = (i == cur ? "• " : "    ") + options[i];
        }
        b.setItems(labels, (d, which) -> {
            org.telegram.messenger.MessagesController.getGlobalMainSettings().edit().putInt(key, which).apply();
            refreshList();
            if ("dg_actionBarTitle".equals(key)) {
                rebuildAppearanceScreens();
            } else if ("dg_dividerStyle".equals(key) && listView != null) {
                listView.invalidateItemDecorations();
            }
        });
        b.setNegativeButton("Отмена", null);
        showDialog(b.create());
    }

    private void rebuildAppearanceScreens() {
        if (parentLayout != null) {
            parentLayout.rebuildFragments(0);
        }
    }

    private void refreshThenRebuildAppearanceScreens() {
        refreshList();
        org.telegram.messenger.AndroidUtilities.runOnUIThread(this::rebuildAppearanceScreens, 180);
    }
    private static final int ID_MD3 = 51;
    private static final int ID_MD3_PROGRESS = 52;
    private static final int ID_MD3_SLIDER = 53;
    private static final int ID_MD3_SWITCH = 54;
    private static final int ID_MD3_TITLE = 55;
    private static final int ID_MD3_BOTTOMNAV = 56;

    private static final String[] MD3_KEYS = {"dg_md3_progress", "dg_md3_slider", "dg_md3_switch", "dg_md3_title", "dg_md3_bottomnav"};

    private static int md3Count() {
        int c = 0;
        for (int i = 0; i < MD3_KEYS.length; i++) {
            if (gPref(MD3_KEYS[i], i < 3)) c++;
        }
        return c;
    }

    private org.telegram.ui.Components.DevGramAvatarPreviewCell avatarPreview;
    private org.telegram.ui.Components.DevGramChatListPreviewCell chatListPreview;
    private org.telegram.ui.Components.DevGramFoldersPreviewCell foldersPreview;
    private org.telegram.ui.Components.DevGramFabPreviewCell fabPreview;
    private org.telegram.ui.Components.DevGramMessagesPreviewCell messagesPreview;

    private View getMessagesPreview() {
        if (messagesPreview == null) {
            messagesPreview = new org.telegram.ui.Components.DevGramMessagesPreviewCell(getContext());
        }
        return messagesPreview;
    }

    private View getAvatarPreview() {
        if (avatarPreview == null) {
            avatarPreview = new org.telegram.ui.Components.DevGramAvatarPreviewCell(getContext());
        }
        return avatarPreview;
    }

    private View getChatListPreview() {
        if (chatListPreview == null) {
            chatListPreview = new org.telegram.ui.Components.DevGramChatListPreviewCell(getContext());
        }
        return chatListPreview;
    }

    private View getFoldersPreview() {
        if (foldersPreview == null) {
            foldersPreview = new org.telegram.ui.Components.DevGramFoldersPreviewCell(getContext());
        }
        return foldersPreview;
    }

    private View getFabPreview() {
        if (fabPreview == null) {
            fabPreview = new org.telegram.ui.Components.DevGramFabPreviewCell(getContext());
            fabPreview.setOnPick(square -> {
                DevGramConfig.setSquareFab(square);
                refreshList();
            });
        }
        return fabPreview;
    }
    // Чаты
    private static final int ID_DISABLE_MARKDOWN = 22;
    private static final int ID_HIDE_KEYBOARD_ON_SCROLL = 23;
    private static final int ID_DISABLE_GREETING = 24;
    private static final int ID_COMMA_AFTER_MENTION = 28;
    private static final int ID_TIME_WITH_SECONDS = 29;
    private static final int ID_REPLACE_FORWARD = 70;
    private static final int ID_MENTION_BY_NAME = 71;
    private static final int ID_HIDE_SEND_AS = 72;
    private static final int ID_DISABLE_LINK_PREVIEW = 73;
    private static final int ID_DISABLE_NEXT_CHANNEL = 74;
    private static final int ID_DISABLE_QUICK_REACTION = 75;
    private static final int ID_HIDE_MESSAGE_REACTIONS = 76;
    private static final int ID_HIDE_SAVED_TAGS = 77;
    private static final int ID_FULL_RECENT_STICKERS = 78;
    private static final int ID_SHOW_ARCHIVED_STICKERS = 79;
    private static final int ID_INAPP_CAMERA = 80;
    private static final int ID_SYSTEM_CAMERA = 81;
    private static final int ID_ALWAYS_HD = 82;
    private static final int ID_REMOVE_MESSAGE_TAIL = 83;
    private static final int ID_REPLACE_EDITED = 84;
    private static final int ID_HIDE_SHARE_BUTTON = 85;
    private static final int ID_DOUBLE_TAP_REACTION = 86;
    private static final int ID_AI_EDITOR = 87;
    private static final int ID_AI_SUMMARIES = 88;
    private static final int ID_PHOTO_HAS_STICKER = 89;
    private static final int ID_DISABLE_MOTION_PHOTO = 90;
    private static final int ID_DISABLE_FLIP_PHOTOS = 91;
    private static final int ID_REAR_VIDEO_MESSAGES = 92;
    private static final int ID_DISABLE_VOLUME_AUTOPLAY = 93;
    private static final int ID_DISABLE_RECENT_FILES = 94;
    private static final int ID_DISABLE_AUTOPLAY_VOICE = 95;
    private static final int ID_PREFER_ORIGINAL_QUALITY = 96;
    private static final int ID_SWIPE_TO_PIP = 97;
    private static final int ID_PAUSE_VIDEO_MINIMIZE = 98;
    private static final int ID_DOUBLE_TAP_SEEK = 99;
    private static final int ID_AI_CHAT = 100;
    private static final int ID_AI_PROVIDER = 101;
    private static final String[] DOUBLE_TAP_SEEK_OPTIONS = {"5 секунд", "10 секунд", "15 секунд", "30 секунд"};

    private final int category;
    private UniversalRecyclerView listView;

    public DevGramCategoryActivity(int category) {
        this.category = category;
    }

    public static String titleFor(int category) {
        switch (category) {
            case CATEGORY_GHOST:
                return "Режим призрака";
            case CATEGORY_SPY:
                return "Слежка";
            case CATEGORY_APPEARANCE:
                return "Внешний вид";
            case CATEGORY_CHATS:
                return "Чаты";
            default:
                return "Основные";
        }
    }

    @Override
    public View createView(Context context) {
        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setAllowOverlayTitle(true);
        actionBar.setTitle(titleFor(category));
        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(int id) {
                if (id == -1) {
                    finishFragment();
                }
            }
        });

        FrameLayout contentView = new FrameLayout(context);
        listView = new UniversalRecyclerView(this, this::fillItems, this::onItemClick, null);
        listView.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundGray, resourceProvider));
        contentView.addView(listView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.FILL));
        actionBar.setAdaptiveBackground(listView);

        return fragmentView = contentView;
    }

    private int ghostCount() {
        int c = 0;
        if (!DevGramConfig.sendReadPackets) c++;
        if (!DevGramConfig.sendOnlinePackets) c++;
        if (!DevGramConfig.sendUploadTyping) c++;
        return c;
    }

    private void fillItems(ArrayList<UItem> items, UniversalAdapter adapter) {
        if (category == CATEGORY_GHOST) {
            items.add(UItem.asCheck(ID_GHOST_MASTER, "Режим призрака  " + ghostCount() + "/3")
                    .setChecked(DevGramConfig.isGhostModeActive()));
            items.add(UItem.asRoundCheckbox(ID_GHOST_READ, "Не отправлять прочтение")
                    .setChecked(!DevGramConfig.sendReadPackets));
            items.add(UItem.asRoundCheckbox(ID_GHOST_ONLINE, "Не показывать «в сети»")
                    .setChecked(!DevGramConfig.sendOnlinePackets));
            items.add(UItem.asRoundCheckbox(ID_GHOST_TYPING, "Не показывать «печатает»")
                    .setChecked(!DevGramConfig.sendUploadTyping));
            items.add(UItem.asShadow("Собеседники не видят ваше прочтение, статус «в сети» и «печатает…»."));
        } else if (category == CATEGORY_SPY) {
            items.add(UItem.asCheck(ID_SAVE_DELETED, "Сохранять удалённые")
                    .setChecked(DevGramConfig.saveDeletedMessages));
            items.add(UItem.asCheck(ID_SAVE_HISTORY, "Сохранять историю изменений")
                    .setChecked(DevGramConfig.saveMessagesHistory));
            items.add(UItem.asCheck(ID_SAVE_MEDIA, "Сохранять вложения")
                    .setChecked(DevGramConfig.saveMedia));
            items.add(UItem.asCheck(ID_SAVE_BOTS, "Сохранять в чатах с ботами")
                    .setChecked(DevGramConfig.saveInBotChats));
            items.add(UItem.asShadow(null));
        } else if (category == CATEGORY_APPEARANCE) {
            // Секции и порядок — как в exteraGram 12.9.0:
            // Закругление аватарок → Список чатов → Папки с чатами → Внешний вид → Настройки размытия

            // — Закругление аватарок — непрерывный слайдер Квадрат..Круг + живое превью
            items.add(UItem.asHeader("Закругление аватарок"));
            items.add(UItem.asIntSlideView(1, 0, avatarCornersInit(), 30,
                    val -> val <= 0 ? "Квадрат" : (val >= 30 ? "Круг" : String.valueOf(val)),
                    val -> {
                        org.telegram.messenger.MessagesController.getGlobalMainSettings()
                                .edit().putFloat("avatarCornersF", val).apply();
                        if (avatarPreview != null) avatarPreview.invalidate();
                        if (chatListPreview != null) chatListPreview.invalidate();
                    }).setId(ID_AVATAR_SHAPE));
            items.add(UItem.asCustom(getAvatarPreview()));
            items.add(UItem.asCheck(ID_SINGLE_CORNER, "Единое закругление")
                    .setChecked(gPref("dg_singleCorner", false)));
            items.add(UItem.asShadow("Форумы будут иметь ту же форму, что и чаты."));

            // — Список чатов —
            items.add(UItem.asHeader("Список чатов"));
            items.add(UItem.asCustom(getChatListPreview()));
            items.add(UItem.asCheck(ID_FORCE_SNOW, "Принудительный снег")
                    .setChecked(DevGramConfig.forceSnow));
            // «Скрыть статус» — только если у пользователя есть эмодзи-статус/премиум
            if (chatListPreview != null && chatListPreview.userHasStatus()) {
                items.add(UItem.asCheck(ID_HIDE_STATUS, "Скрыть статус")
                        .setChecked(gPref("dg_hideStatus", false)));
            }
            items.add(UItem.asCheck(ID_CENTER_TITLE, "Заголовок по центру")
                    .setChecked(DevGramConfig.centerTitle));
            items.add(UItem.asCheck(ID_HIDE_STORIES, "Скрыть истории")
                    .setChecked(gPref("dg_hideStories", false)));
            items.add(UItem.asCheck(ID_HIDE_FAB, "Скрыть плавающую кнопку")
                    .setChecked(gPref("dg_hideFab", false)));
            items.add(UItem.asCheck(ID_HIDE_SEARCHBAR, "Скрыть строку поиска")
                    .setChecked(gPref("dg_hideSearchBar", false)));
            items.add(UItem.asCheck(ID_MINI_AVATARS, "Мини-аватарки отправителей")
                    .setChecked(!gPref("disableThumbsInDialogList", false)));
            items.add(UItem.asButton(ID_ACTIONBAR_TITLE, "Текст в заголовке",
                    ACTIONBAR_TITLE_OPTIONS[gInt("dg_actionBarTitle", 0)]));
            items.add(UItem.asShadow("Падающий снег появится в верхней панели."));

            // — Папки с чатами —
            items.add(UItem.asHeader("Папки с чатами"));
            items.add(UItem.asCustom(getFoldersPreview()));
            items.add(UItem.asSlideView(
                    new String[]{"Текст", "Значок + текст", "Значок"},
                    DevGramConfig.getFolderTabsStyle(),
                    index -> {
                        DevGramConfig.setFolderTabsStyle(index);
                        if (foldersPreview != null) foldersPreview.invalidate();
                    }).setId(ID_FOLDER_TABS_STYLE));
            items.add(UItem.asCheck(ID_TAB_COUNTER, "Счётчик уведомлений")
                    .setChecked(gPref("dg_tabCounter", true)));
            items.add(UItem.asCheck(ID_HIDE_ALL_CHATS, "Скрыть вкладку «Все чаты»")
                    .setChecked(gPref("hideAllChatsTab", false)));
            items.add(UItem.asShadow("Иконки папок синхронизируются с вашим аккаунтом."));

            // — Внешний вид —
            items.add(UItem.asHeader("Внешний вид"));
            items.add(UItem.asCustom(getFabPreview()));
            items.add(UItem.asCheck(ID_SYSTEM_FONTS, "Системные шрифты")
                    .setChecked(gPref("dg_systemFonts", true)));
            items.add(UItem.asCheck(ID_SYSTEM_EMOJI, "Системные эмодзи")
                    .setChecked(DevGramConfig.isUseSystemEmoji()));
            items.add(UItem.asCheck(ID_MD3, "Material Design 3  " + md3Count() + "/5")
                    .setChecked(gPref("dg_md3", true)));
            if (gPref("dg_md3", true)) {
                items.add(UItem.asRoundCheckbox(ID_MD3_PROGRESS, "Индикаторы загрузки")
                        .setChecked(gPref("dg_md3_progress", true)));
                items.add(UItem.asRoundCheckbox(ID_MD3_SLIDER, "Стиль слайдера")
                        .setChecked(gPref("dg_md3_slider", true)));
                items.add(UItem.asRoundCheckbox(ID_MD3_SWITCH, "Стиль переключателей")
                        .setChecked(gPref("dg_md3_switch", true)));
                items.add(UItem.asRoundCheckbox(ID_MD3_TITLE, "Заголовок чата")
                        .setChecked(gPref("dg_md3_title", false)));
                items.add(UItem.asRoundCheckbox(ID_MD3_BOTTOMNAV, "Нижняя панель навигации")
                        .setChecked(gPref("dg_md3_bottomnav", false)));
            }
            items.add(UItem.asCheck(ID_GOOEY, "«Липкая» анимация аватарок")
                    .setChecked(gPref("dg_gooey", true)));
            items.add(UItem.asCheck(ID_CUSTOM_THEMES, "Различные темы в чатах")
                    .setChecked(gPref("dg_customThemes", true)));
            items.add(UItem.asShadow("Каждый чат будет отображаться с той темой, которая была "
                    + "выбрана специально для него."));

            // — Секции —
            items.add(UItem.asHeader("Секции"));
            items.add(UItem.asIntSlideView(1, 0, gInt("dg_sectionRadius", 20), 28,
                    val -> val <= 0 ? "Откл." : (val >= 28 ? "Макс" : val + " dp"),
                    val -> org.telegram.messenger.MessagesController.getGlobalMainSettings()
                            .edit().putInt("dg_sectionRadius", val).apply()).setId(ID_STICKER_SIZE + 100));
            items.add(UItem.asCheck(ID_SEPARATE_HEADERS, "Отделить заголовки")
                    .setChecked(gPref("dg_separateHeaders", true))
                    .setEnabled(gInt("dg_dividerStyle", 1) != 2));
            items.add(UItem.asButton(ID_DIVIDER_STYLE, "Тип разделителей",
                    DIVIDER_STYLE_OPTIONS[gInt("dg_dividerStyle", 1)]));
            items.add(UItem.asShadow(null));

            // — Настройки размытия — (порядок как у exteraGram: обводка → меню → размытие)
            items.add(UItem.asHeader("Настройки размытия"));
            items.add(UItem.asButton(ID_GLASS_OUTLINE, "Обводка стекла",
                    GLASS_OUTLINE_OPTIONS[gInt("dg_glassOutline", 0)]));
            items.add(UItem.asCheck(ID_GLASS_MENU, "Стеклянное меню сообщения")
                    .setChecked(DevGramConfig.glassMenu));
            items.add(UItem.asCheck(ID_CHAT_BLUR, "Принудительное размытие")
                    .setChecked(org.telegram.messenger.LiteMode.isEnabled(org.telegram.messenger.LiteMode.FLAG_CHAT_BLUR)));
            items.add(UItem.asShadow("Меню сообщения и панель реакций становятся матовым стеклом. "
                    + "Стеклу нужно размытие — включите «Принудительное размытие»."));
        } else if (category == CATEGORY_CHATS) {
            // Порядок основан на ChatsPreferencesActivity из ExteraGram. Уже имевшиеся
            // функции DevGram не потеряны: они разнесены по подходящим секциям.
            items.add(UItem.asHeader("Стикеры"));
            items.add(UItem.asIntSlideView(1, 2, (int) DevGramConfig.getStickerSize(), 14,
                    val -> String.valueOf(val),
                    val -> DevGramConfig.setStickerSize(val)).setId(ID_STICKER_SIZE));
            items.add(UItem.asCheck(ID_FULL_RECENT_STICKERS, "Не ограничивать недавние стикеры")
                    .setChecked(gPref("fullRecentStickers", true)));
            items.add(UItem.asCheck(ID_SHOW_ARCHIVED_STICKERS, "Показывать архивные стикеры")
                    .setChecked(gPref("showArchivedStickers", false)));
            items.add(UItem.asCheck(ID_HIDE_EMOJI_CATEGORIES, "Скрыть категории в поиске эмодзи")
                    .setChecked(DevGramConfig.hideEmojiCategories));
            items.add(UItem.asShadow(null));

            items.add(UItem.asHeader("Двойное нажатие"));
            items.add(UItem.asButton(ID_DOUBLE_TAP_REACTION, "Реакция по двойному нажатию",
                    org.telegram.messenger.MediaDataController.getInstance(currentAccount).getDoubleTapReaction()));
            items.add(UItem.asShadow("Откроется экран выбора реакции с интерактивным превью."));

            items.add(UItem.asHeader("Поведение чата"));
            items.add(UItem.asCheck(ID_DISABLE_MARKDOWN, "Отключить Markdown")
                    .setChecked(DevGramConfig.disableMarkdown));
            items.add(UItem.asCheck(ID_REPLACE_FORWARD, "Заменять пересылку")
                    .setChecked(gPref("replaceForward", true)));
            items.add(UItem.asCheck(ID_MENTION_BY_NAME, "Упоминать по имени")
                    .setChecked(gPref("mentionByName", false)));
            items.add(UItem.asCheck(ID_HIDE_SEND_AS, "Скрыть кнопку «Отправить от имени»")
                    .setChecked(gPref("hideSendAs", false)));
            items.add(UItem.asCheck(ID_DISABLE_LINK_PREVIEW, "Не создавать предпросмотр ссылок")
                    .setChecked(gPref("disableLinkPreviewByDefault", false)));
            items.add(UItem.asCheck(ID_HIDE_KEYBOARD_ON_SCROLL, "Скрывать клавиатуру при прокрутке")
                    .setChecked(DevGramConfig.hideKeyboardOnScroll));
            items.add(UItem.asCheck(ID_DISABLE_GREETING, "Скрыть приветственный стикер")
                    .setChecked(DevGramConfig.disableGreetingSticker));
            items.add(UItem.asCheck(ID_COMMA_AFTER_MENTION, "Запятая после упоминания")
                    .setChecked(DevGramConfig.addCommaAfterMention));
            items.add(UItem.asCheck(ID_DISABLE_NEXT_CHANNEL, "Отключить переход к следующему каналу")
                    .setChecked(gPref("disableSlideToNextChannel", false)));
            items.add(UItem.asCheck(ID_TIME_WITH_SECONDS, "Время с секундами")
                    .setChecked(DevGramConfig.isFormatWithSeconds()));
            items.add(UItem.asShadow("Настройки применяются к полю ввода и навигации внутри чатов."));

            items.add(UItem.asHeader("Сообщения"));
            items.add(UItem.asCustom(getMessagesPreview()));
            items.add(UItem.asCheck(ID_REMOVE_MESSAGE_TAIL, "Убрать хвост сообщения")
                    .setChecked(DevGramConfig.removeMessageTail));
            items.add(UItem.asCheck(ID_REPLACE_EDITED, "Заменить «изменено» значком")
                    .setChecked(DevGramConfig.replaceEditedWithIcon));
            items.add(UItem.asCheck(ID_HIDE_SHARE_BUTTON, "Скрыть кнопку «Поделиться»")
                    .setChecked(DevGramConfig.hideShareButton));
            items.add(UItem.asShadow("Изменения сразу отображаются на превью выше."));

            items.add(UItem.asHeader("Реакции"));
            items.add(UItem.asCheck(ID_DISABLE_QUICK_REACTION, "Отключить быструю реакцию")
                    .setChecked(gPref("disableQuickReaction", false)));
            items.add(UItem.asCheck(ID_HIDE_MESSAGE_REACTIONS, "Скрыть реакции под сообщениями")
                    .setChecked(gPref("hideMessageReactions", false)));
            items.add(UItem.asCheck(ID_HIDE_SAVED_TAGS, "Скрыть теги в Избранном")
                    .setChecked(gPref("hideSavedMessagesTags", false)));
            items.add(UItem.asShadow(null));

            items.add(UItem.asHeader("Функции ИИ"));
            items.add(UItem.asButton(ID_AI_CHAT, "ИИ-чат",
                    org.telegram.messenger.DevGramAiClient.isConfigured() ? "Готов" : "Нужен API-ключ"));
            items.add(UItem.asButton(ID_AI_PROVIDER, "Провайдер ИИ",
                    gPrefString("dg_aiModel", "gpt-4o-mini")));
            items.add(UItem.asCheck(ID_AI_EDITOR, "Редактор текста с ИИ")
                    .setChecked(!gPref("hideAiEditor", false)));
            items.add(UItem.asCheck(ID_AI_SUMMARIES, "Краткие пересказы сообщений")
                    .setChecked(!gPref("dg_hideAiSummaries", false)));
            items.add(UItem.asShadow("Редактор доступен в поле ввода и подписях, пересказ — у длинных сообщений."));

            items.add(UItem.asHeader("Медиа"));
            items.add(UItem.asCheck(ID_INAPP_CAMERA, "Камера внутри приложения")
                    .setChecked(org.telegram.messenger.SharedConfig.inappCamera));
            items.add(UItem.asCheck(ID_SYSTEM_CAMERA, "Использовать системную камеру")
                    .setChecked(gPref("systemCamera", false))
                    .setEnabled(org.telegram.messenger.SharedConfig.inappCamera));
            items.add(UItem.asCheck(ID_ALWAYS_HD, "Всегда отправлять фото в HD")
                    .setChecked(org.telegram.messenger.SharedConfig.photoHighQualityDefault));
            items.add(UItem.asCheck(ID_PHOTO_HAS_STICKER, "Помечать фото со стикерами")
                    .setChecked(gPref("photoHasSticker", true)));
            items.add(UItem.asCheck(ID_DISABLE_MOTION_PHOTO, "Отключить Motion Photo")
                    .setChecked(gPref("disableMotionPhoto", false)));
            items.add(UItem.asCheck(ID_DISABLE_FLIP_PHOTOS, "Не отражать фотографии")
                    .setChecked(gPref("disableFlipPhotos", false)));
            items.add(UItem.asCheck(ID_REAR_VIDEO_MESSAGES, "Кружки с задней камеры")
                    .setChecked(gPref("rearVideoMessages", false)));
            items.add(UItem.asCheck(ID_DISABLE_VOLUME_AUTOPLAY, "Не запускать видео кнопкой громкости")
                    .setChecked(gPref("disablePlayVisibleVideoOnVolume", false)));
            items.add(UItem.asCheck(ID_DISABLE_RECENT_FILES, "Скрыть недавние файлы во вложениях")
                    .setChecked(gPref("disableRecentFilesAttachment", false)));
            items.add(UItem.asCheck(ID_PREFER_ORIGINAL_QUALITY, "Предпочитать исходное качество видео")
                    .setChecked(gPref("dg_preferOriginalQuality", false)));
            items.add(UItem.asButton(ID_DOUBLE_TAP_SEEK, "Перемотка двойным нажатием",
                    DOUBLE_TAP_SEEK_OPTIONS[Math.max(0, Math.min(3, gInt("dg_doubleTapSeek", 1)))]));
            items.add(UItem.asCheck(ID_SWIPE_TO_PIP, "Смахивание в картинку-в-картинке")
                    .setChecked(gPref("dg_swipeToPip", true)));
            items.add(UItem.asCheck(ID_PAUSE_VIDEO_MINIMIZE, "Ставить видео на паузу при сворачивании")
                    .setChecked(gPref("dg_pauseVideoOnMinimize", false)));
            items.add(UItem.asShadow(null));

            items.add(UItem.asHeader("Голосовые сообщения"));
            items.add(UItem.asCheck(ID_DISABLE_AUTOPLAY_VOICE, "Не включать следующее голосовое автоматически")
                    .setChecked(gPref("disableAutoplayNextVoice", false)));
            items.add(UItem.asShadow(null));

            items.add(UItem.asHeader("Отображение"));
            items.add(UItem.asCheck(ID_NUMBER_ROUNDING, "Отключить округление чисел")
                    .setChecked(DevGramConfig.disableNumberRounding));
            items.add(UItem.asShadow(null));
        } else {
            items.add(UItem.asCheck(ID_SHOW_CONTACTS, "Показывать «Контакты» в нижнем меню")
                    .setChecked(getUserConfig().showContactsTab));
            items.add(UItem.asCheck(ID_DISABLE_ADS, "Скрывать рекламу")
                    .setChecked(DevGramConfig.disableAds));
            items.add(UItem.asCheck(ID_LOCAL_PREMIUM, "Локальный премиум")
                    .setChecked(DevGramConfig.localPremium));
            items.add(UItem.asCheck(ID_STREAKS, "Огоньки (серии)")
                    .setChecked(DevGramConfig.streaksEnabled));
            items.add(UItem.asShadow("🔥N рядом с именем — сколько дней подряд вы общаетесь в личке "
                    + "(показывается от 3 дней). Выключишь — огоньки пропадут у всех."));

            items.add(UItem.asCheck(ID_IOS_PROFILE, "Профиль в стиле iOS")
                    .setChecked(DevGramConfig.iosProfile));
            items.add(UItem.asShadow("Аватар и имя по центру, круглые стеклянные кнопки действий и "
                    + "скруглённые полупрозрачные карточки — как на iOS. Применяется ко всем профилям. "
                    + "Открой профиль заново, чтобы применить."));

            // Прокси доступен обладателям значка: команда (✅), поддержавшие (✈️), официальные
            long uid = getUserConfig().getClientUserId();
            long myBadge = org.telegram.messenger.DevGramBadges.emojiIdOf(uid);
            boolean hasArrow = org.telegram.messenger.DevGramBadges.isTeam(uid)
                    || myBadge == org.telegram.messenger.DevGramBadges.EMOJI_SUPPORTER
                    || myBadge == org.telegram.messenger.DevGramBadges.EMOJI_OFFICIAL;
            if (hasArrow) {
                items.add(UItem.asCheck(ID_VPN, "Прокси")
                        .setChecked(org.telegram.messenger.DevGramProxy.isEnabled()));
                items.add(UItem.asShadow(null));
            }
        }
    }

    private void onItemClick(UItem item, View view, int position, float x, float y) {
        if (item.id == ID_GHOST_MASTER) {
            DevGramConfig.toggleGhostMode();
        } else if (item.id == ID_GHOST_READ) {
            DevGramConfig.setSendReadPackets(!DevGramConfig.sendReadPackets);
        } else if (item.id == ID_GHOST_ONLINE) {
            DevGramConfig.setSendOnlinePackets(!DevGramConfig.sendOnlinePackets);
        } else if (item.id == ID_GHOST_TYPING) {
            DevGramConfig.setSendUploadTyping(!DevGramConfig.sendUploadTyping);
        } else if (item.id == ID_SAVE_DELETED) {
            DevGramConfig.setSaveDeletedMessages(!DevGramConfig.saveDeletedMessages);
        } else if (item.id == ID_SAVE_HISTORY) {
            DevGramConfig.setSaveMessagesHistory(!DevGramConfig.saveMessagesHistory);
        } else if (item.id == ID_SAVE_MEDIA) {
            DevGramConfig.setSaveMedia(!DevGramConfig.saveMedia);
        } else if (item.id == ID_SAVE_BOTS) {
            DevGramConfig.setSaveInBotChats(!DevGramConfig.saveInBotChats);
        } else if (item.id == ID_DISABLE_ADS) {
            DevGramConfig.setDisableAds(!DevGramConfig.disableAds);
        } else if (item.id == ID_LOCAL_PREMIUM) {
            DevGramConfig.setLocalPremium(!DevGramConfig.localPremium);
        } else if (item.id == ID_STREAKS) {
            DevGramConfig.setStreaksEnabled(!DevGramConfig.streaksEnabled);
        } else if (item.id == ID_IOS_PROFILE) {
            DevGramConfig.setIosProfile(!DevGramConfig.iosProfile);
        } else if (item.id == ID_VPN) {
            boolean enable = !org.telegram.messenger.DevGramProxy.isEnabled();
            org.telegram.messenger.DevGramProxy.setEnabled(enable);
            DevGramConfig.setVpnEnabled(enable);
        } else if (item.id == ID_VPN_DIAG) {
            runProxyDiagnostics();
            return;
        } else if (item.id == ID_SHOW_CONTACTS) {
            getUserConfig().setShowContactsTab(!getUserConfig().showContactsTab);
            NotificationCenter.getInstance(currentAccount).postNotificationName(NotificationCenter.contactsTabVisibleToggled);
        } else if (item.id == ID_NUMBER_ROUNDING) {
            DevGramConfig.setDisableNumberRounding(!DevGramConfig.disableNumberRounding);
        } else if (item.id == ID_SQUARE_FAB) {
            DevGramConfig.setSquareFab(!DevGramConfig.squareFab);
        } else if (item.id == ID_GLASS_MENU) {
            DevGramConfig.setGlassMenu(!DevGramConfig.glassMenu);
        } else if (item.id == ID_HIDE_EMOJI_CATEGORIES) {
            DevGramConfig.setHideEmojiCategories(!DevGramConfig.hideEmojiCategories);
        } else if (item.id == ID_SYSTEM_EMOJI) {
            DevGramConfig.setUseSystemEmoji(!DevGramConfig.isUseSystemEmoji());
        } else if (item.id == ID_FORCE_SNOW) {
            DevGramConfig.setForceSnow(!DevGramConfig.forceSnow);
        } else if (item.id == ID_CHAT_BLUR) {
            org.telegram.messenger.SharedConfig.toggleChatBlur();
        } else if (item.id == ID_SINGLE_CORNER) {
            gToggle("dg_singleCorner", false);
            refreshThenRebuildAppearanceScreens();
            return;
        } else if (item.id == ID_HIDE_STATUS) {
            gToggle("dg_hideStatus", false);
            if (chatListPreview != null) chatListPreview.invalidate();
            refreshThenRebuildAppearanceScreens();
            return;
        } else if (item.id == ID_HIDE_STORIES) {
            gToggle("dg_hideStories", false);
            getNotificationCenter().postNotificationName(NotificationCenter.storiesEnabledUpdate);
        } else if (item.id == ID_HIDE_FAB) {
            gToggle("dg_hideFab", false);
            refreshThenRebuildAppearanceScreens();
            return;
        } else if (item.id == ID_HIDE_SEARCHBAR) {
            gToggle("dg_hideSearchBar", false);
            refreshThenRebuildAppearanceScreens();
            return;
        } else if (item.id == ID_TAB_COUNTER) {
            gToggle("dg_tabCounter", true);
            if (foldersPreview != null) foldersPreview.invalidate();
        } else if (item.id == ID_SYSTEM_FONTS) {
            gToggle("dg_systemFonts", true);
            org.telegram.messenger.AndroidUtilities.invalidateDevgramSystemFonts();
        } else if (item.id == ID_GOOEY) {
            gToggle("dg_gooey", true);
        } else if (item.id == ID_CUSTOM_THEMES) {
            gToggle("dg_customThemes", true);
        } else if (item.id == ID_ACTIONBAR_TITLE) {
            showChoice("Текст в заголовке", ACTIONBAR_TITLE_OPTIONS, "dg_actionBarTitle", 0);
            return;
        } else if (item.id == ID_DIVIDER_STYLE) {
            showChoice("Тип разделителей", DIVIDER_STYLE_OPTIONS, "dg_dividerStyle", 1);
            return;
        } else if (item.id == ID_GLASS_OUTLINE) {
            showChoice("Обводка стекла", GLASS_OUTLINE_OPTIONS, "dg_glassOutline", 0);
            return;
        } else if (item.id == ID_SEPARATE_HEADERS) {
            if (gInt("dg_dividerStyle", 1) != 2) {
                gToggle("dg_separateHeaders", true);
            }
        } else if (item.id == ID_MD3) {
            gToggle("dg_md3", true);
            refreshThenRebuildAppearanceScreens();
            return;
        } else if (item.id == ID_MD3_PROGRESS) {
            gToggle("dg_md3_progress", true);
        } else if (item.id == ID_MD3_SLIDER) {
            gToggle("dg_md3_slider", true);
        } else if (item.id == ID_MD3_SWITCH) {
            gToggle("dg_md3_switch", true);
        } else if (item.id == ID_MD3_TITLE) {
            gToggle("dg_md3_title", false);
            refreshThenRebuildAppearanceScreens();
            return;
        } else if (item.id == ID_MD3_BOTTOMNAV) {
            gToggle("dg_md3_bottomnav", false);
            refreshThenRebuildAppearanceScreens();
            return;
        } else if (item.id == ID_CENTER_TITLE) {
            DevGramConfig.setCenterTitle(!DevGramConfig.centerTitle);
            if (chatListPreview != null) chatListPreview.invalidate();
            refreshThenRebuildAppearanceScreens();
            return;
        } else if (item.id == ID_HIDE_ALL_CHATS) {
            gToggle("hideAllChatsTab", false);
            refreshThenRebuildAppearanceScreens();
            return;
        } else if (item.id == ID_MINI_AVATARS) {
            gToggle("disableThumbsInDialogList", false);
        } else if (item.id == ID_HIDE_CONTACTS_DIALOGS) {
            gToggle("hideContactsInDialogs", false);
        } else if (item.id == ID_LAST_SEEN_DOTS) {
            gToggle("enableLastSeenDots", false);
        } else if (item.id == ID_DISABLE_MARKDOWN) {
            DevGramConfig.setDisableMarkdown(!DevGramConfig.disableMarkdown);
        } else if (item.id == ID_HIDE_KEYBOARD_ON_SCROLL) {
            DevGramConfig.setHideKeyboardOnScroll(!DevGramConfig.hideKeyboardOnScroll);
        } else if (item.id == ID_DISABLE_GREETING) {
            DevGramConfig.setDisableGreetingSticker(!DevGramConfig.disableGreetingSticker);
        } else if (item.id == ID_COMMA_AFTER_MENTION) {
            DevGramConfig.setAddCommaAfterMention(!DevGramConfig.addCommaAfterMention);
        } else if (item.id == ID_TIME_WITH_SECONDS) {
            DevGramConfig.setFormatWithSeconds(!DevGramConfig.isFormatWithSeconds());
        } else if (item.id == ID_REPLACE_FORWARD) {
            gToggle("replaceForward", true);
        } else if (item.id == ID_MENTION_BY_NAME) {
            gToggle("mentionByName", false);
        } else if (item.id == ID_HIDE_SEND_AS) {
            gToggle("hideSendAs", false);
        } else if (item.id == ID_DISABLE_LINK_PREVIEW) {
            gToggle("disableLinkPreviewByDefault", false);
        } else if (item.id == ID_DISABLE_NEXT_CHANNEL) {
            gToggle("disableSlideToNextChannel", false);
        } else if (item.id == ID_DISABLE_QUICK_REACTION) {
            gToggle("disableQuickReaction", false);
        } else if (item.id == ID_HIDE_MESSAGE_REACTIONS) {
            gToggle("hideMessageReactions", false);
        } else if (item.id == ID_HIDE_SAVED_TAGS) {
            gToggle("hideSavedMessagesTags", false);
        } else if (item.id == ID_FULL_RECENT_STICKERS) {
            gToggle("fullRecentStickers", true);
        } else if (item.id == ID_SHOW_ARCHIVED_STICKERS) {
            gToggle("showArchivedStickers", false);
            if (gPref("showArchivedStickers", false)) {
                org.telegram.messenger.MediaDataController.getInstance(currentAccount).loadArchivedStickerSets();
            }
        } else if (item.id == ID_INAPP_CAMERA) {
            org.telegram.messenger.SharedConfig.toggleInappCamera();
        } else if (item.id == ID_SYSTEM_CAMERA) {
            if (org.telegram.messenger.SharedConfig.inappCamera) {
                gToggle("systemCamera", false);
            }
        } else if (item.id == ID_ALWAYS_HD) {
            org.telegram.messenger.SharedConfig.photoHighQualityDefault =
                    !org.telegram.messenger.SharedConfig.photoHighQualityDefault;
            org.telegram.messenger.MessagesController.getGlobalMainSettings().edit()
                    .putBoolean("photoHighQualityDefault",
                            org.telegram.messenger.SharedConfig.photoHighQualityDefault).apply();
        } else if (item.id == ID_REMOVE_MESSAGE_TAIL) {
            DevGramConfig.setRemoveMessageTail(!DevGramConfig.removeMessageTail);
            if (messagesPreview != null) messagesPreview.invalidate();
        } else if (item.id == ID_REPLACE_EDITED) {
            DevGramConfig.setReplaceEditedWithIcon(!DevGramConfig.replaceEditedWithIcon);
            if (messagesPreview != null) messagesPreview.invalidate();
        } else if (item.id == ID_HIDE_SHARE_BUTTON) {
            DevGramConfig.setHideShareButton(!DevGramConfig.hideShareButton);
            if (messagesPreview != null) messagesPreview.invalidate();
        } else if (item.id == ID_DOUBLE_TAP_REACTION) {
            presentFragment(new org.telegram.ui.ReactionsDoubleTapManageActivity());
            return;
        } else if (item.id == ID_AI_EDITOR) {
            gToggle("hideAiEditor", false);
        } else if (item.id == ID_AI_SUMMARIES) {
            gToggle("dg_hideAiSummaries", false);
        } else if (item.id == ID_AI_CHAT) {
            if (!org.telegram.messenger.DevGramAiClient.isConfigured()) {
                showAiProviderDialog(true);
            } else {
                presentFragment(new DevGramAiChatActivity());
            }
            return;
        } else if (item.id == ID_AI_PROVIDER) {
            showAiProviderDialog(false);
            return;
        } else if (item.id == ID_PHOTO_HAS_STICKER) {
            gToggle("photoHasSticker", true);
        } else if (item.id == ID_DISABLE_MOTION_PHOTO) {
            gToggle("disableMotionPhoto", false);
        } else if (item.id == ID_DISABLE_FLIP_PHOTOS) {
            gToggle("disableFlipPhotos", false);
        } else if (item.id == ID_REAR_VIDEO_MESSAGES) {
            gToggle("rearVideoMessages", false);
        } else if (item.id == ID_DISABLE_VOLUME_AUTOPLAY) {
            gToggle("disablePlayVisibleVideoOnVolume", false);
        } else if (item.id == ID_DISABLE_RECENT_FILES) {
            gToggle("disableRecentFilesAttachment", false);
        } else if (item.id == ID_DISABLE_AUTOPLAY_VOICE) {
            gToggle("disableAutoplayNextVoice", false);
        } else if (item.id == ID_PREFER_ORIGINAL_QUALITY) {
            gToggle("dg_preferOriginalQuality", false);
        } else if (item.id == ID_SWIPE_TO_PIP) {
            gToggle("dg_swipeToPip", true);
        } else if (item.id == ID_PAUSE_VIDEO_MINIMIZE) {
            gToggle("dg_pauseVideoOnMinimize", false);
        } else if (item.id == ID_DOUBLE_TAP_SEEK) {
            showChoice("Перемотка двойным нажатием", DOUBLE_TAP_SEEK_OPTIONS,
                    "dg_doubleTapSeek", 1);
            return;
        } else {
            return;
        }
        refreshList();
    }

    private void refreshList() {
        if (listView != null && listView.adapter != null) {
            listView.adapter.update(true);
        }
    }

    private static String gPrefString(String key, String def) {
        return org.telegram.messenger.MessagesController.getGlobalMainSettings().getString(key, def);
    }

    private void showAiProviderDialog(boolean openChatAfterSave) {
        if (getParentActivity() == null) return;
        android.widget.LinearLayout box = new android.widget.LinearLayout(getParentActivity());
        box.setOrientation(android.widget.LinearLayout.VERTICAL);
        int pad = org.telegram.messenger.AndroidUtilities.dp(20);
        box.setPadding(pad, 0, pad, 0);
        android.widget.EditText endpoint = new android.widget.EditText(getParentActivity());
        endpoint.setHint("Endpoint");
        endpoint.setSingleLine(true);
        endpoint.setText(gPrefString("dg_aiEndpoint", "https://api.openai.com/v1/chat/completions"));
        box.addView(endpoint, new android.widget.LinearLayout.LayoutParams(-1, -2));
        android.widget.EditText model = new android.widget.EditText(getParentActivity());
        model.setHint("Модель");
        model.setSingleLine(true);
        model.setText(gPrefString("dg_aiModel", "gpt-4o-mini"));
        box.addView(model, new android.widget.LinearLayout.LayoutParams(-1, -2));
        android.widget.EditText key = new android.widget.EditText(getParentActivity());
        key.setHint("API-ключ");
        key.setSingleLine(true);
        key.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD);
        key.setText(gPrefString("dg_aiKey", ""));
        box.addView(key, new android.widget.LinearLayout.LayoutParams(-1, -2));
        org.telegram.ui.ActionBar.AlertDialog.Builder builder =
                new org.telegram.ui.ActionBar.AlertDialog.Builder(getParentActivity());
        builder.setTitle("Провайдер ИИ");
        builder.setView(box);
        builder.setPositiveButton("Сохранить", (dialog, which) -> {
            org.telegram.messenger.MessagesController.getGlobalMainSettings().edit()
                    .putString("dg_aiEndpoint", endpoint.getText().toString().trim())
                    .putString("dg_aiModel", model.getText().toString().trim())
                    .putString("dg_aiKey", key.getText().toString().trim()).apply();
            refreshList();
            if (openChatAfterSave && org.telegram.messenger.DevGramAiClient.isConfigured()) {
                presentFragment(new DevGramAiChatActivity());
            }
        });
        builder.setNegativeButton("Отмена", null);
        showDialog(builder.create());
    }

    // Диагностика прокси: soket-проверка блокирующая → гоним в фоне, результат в диалоге.
    private void runProxyDiagnostics() {
        final org.telegram.ui.ActionBar.AlertDialog progress =
                new org.telegram.ui.ActionBar.AlertDialog(getParentActivity(), org.telegram.ui.ActionBar.AlertDialog.ALERT_TYPE_SPINNER);
        progress.setCanCancel(false);
        progress.show();
        new Thread(() -> {
            final String report = org.telegram.messenger.DevGramProxy.diagnose();
            org.telegram.messenger.AndroidUtilities.runOnUIThread(() -> {
                try {
                    progress.dismiss();
                } catch (Throwable ignore) {
                }
                if (getParentActivity() == null) {
                    return;
                }
                org.telegram.ui.ActionBar.AlertDialog.Builder b =
                        new org.telegram.ui.ActionBar.AlertDialog.Builder(getParentActivity());
                b.setTitle("Диагностика прокси");
                b.setMessage(report);
                b.setPositiveButton("Копировать", (d, w) -> {
                    org.telegram.messenger.AndroidUtilities.addToClipboard(report);
                    org.telegram.ui.Components.BulletinFactory.of(this)
                            .createCopyBulletin("Скопировано").show();
                });
                b.setNegativeButton("Закрыть", null);
                showDialog(b.create());
            });
        }, "DevGramProxyDiag").start();
    }
}

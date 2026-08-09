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

    // стартовое значение слайдера закругления: -1 (не задано) → 30 (круг, как раньше по умолчанию)
    private static int avatarCornersInit() {
        float f = org.telegram.messenger.AndroidUtilities.avatarCornersF();
        if (f < 0) {
            return org.telegram.messenger.AndroidUtilities.avatarCornersType()
                    == org.telegram.messenger.AndroidUtilities.AVATAR_CORNERS_SQUARE ? 0 : 30;
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

    // MD3 (master + 5 подпунктов) и прочие пункты-заглушки (движок позже) — pref-ключи dg_*
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
    private static final int ID_HIDE_DIVIDERS = 57;
    private static final int ID_FULL_DIVIDERS = 58;
    private static final int ID_GLARE = 50;
    private static final int ID_MD3 = 51;
    private static final int ID_MD3_PROGRESS = 52;
    private static final int ID_MD3_SLIDER = 53;
    private static final int ID_MD3_SWITCH = 54;
    private static final int ID_MD3_TITLE = 55;
    private static final int ID_MD3_BOTTOMNAV = 56;

    private static final String[] MD3_KEYS = {"dg_md3_progress", "dg_md3_slider", "dg_md3_switch", "dg_md3_title", "dg_md3_bottomnav"};

    private static int md3Count() {
        int c = 0;
        for (String k : MD3_KEYS) if (gPref(k, true)) c++;
        return c;
    }

    private org.telegram.ui.Components.DevGramAvatarPreviewCell avatarPreview;
    private org.telegram.ui.Components.DevGramChatListPreviewCell chatListPreview;
    private org.telegram.ui.Components.DevGramFoldersPreviewCell foldersPreview;
    private org.telegram.ui.Components.DevGramFabPreviewCell fabPreview;

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
                    .setChecked(gPref("dg_systemFonts", false)));
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
                        .setChecked(gPref("dg_md3_title", true)));
                items.add(UItem.asRoundCheckbox(ID_MD3_BOTTOMNAV, "Нижняя панель навигации")
                        .setChecked(gPref("dg_md3_bottomnav", false)));
            }
            items.add(UItem.asCheck(ID_GOOEY, "«Липкая» анимация аватарок")
                    .setChecked(gPref("dg_gooey", false)));
            items.add(UItem.asCheck(ID_CUSTOM_THEMES, "Различные темы в чатах")
                    .setChecked(gPref("dg_customThemes", false)));
            items.add(UItem.asShadow("Каждый чат будет отображаться с той темой, которая была "
                    + "выбрана специально для него."));

            // — Секции —
            items.add(UItem.asHeader("Секции"));
            items.add(UItem.asIntSlideView(1, 0, gInt("dg_sectionRadius", 20), 30,
                    val -> val <= 0 ? "Откл." : (val >= 30 ? "Макс" : val + " dp"),
                    val -> org.telegram.messenger.MessagesController.getGlobalMainSettings()
                            .edit().putInt("dg_sectionRadius", val).apply()).setId(ID_STICKER_SIZE + 100));
            items.add(UItem.asCheck(ID_SEPARATE_HEADERS, "Отделить заголовки")
                    .setChecked(gPref("dg_separateHeaders", false)));
            items.add(UItem.asCheck(ID_HIDE_DIVIDERS, "Скрыть разделители")
                    .setChecked(gPref("dg_hideDividers", false)));
            items.add(UItem.asCheck(ID_FULL_DIVIDERS, "Разделители во всю ширину")
                    .setChecked(gPref("dg_fullDividers", false)));
            items.add(UItem.asShadow(null));

            // — Настройки размытия —
            items.add(UItem.asHeader("Настройки размытия"));
            items.add(UItem.asCheck(ID_GLARE, "Блики на элементах")
                    .setChecked(gPref("dg_glare", true)));
            items.add(UItem.asCheck(ID_CHAT_BLUR, "Принудительное размытие")
                    .setChecked(org.telegram.messenger.LiteMode.isEnabled(org.telegram.messenger.LiteMode.FLAG_CHAT_BLUR)));
            items.add(UItem.asShadow("Эффекты размытия будут применяться ко всем темам."));
        } else if (category == CATEGORY_CHATS) {
            items.add(UItem.asCheck(ID_DISABLE_MARKDOWN, "Отключить Markdown")
                    .setChecked(DevGramConfig.disableMarkdown));
            items.add(UItem.asCheck(ID_HIDE_KEYBOARD_ON_SCROLL, "Скрывать клавиатуру при прокрутке")
                    .setChecked(DevGramConfig.hideKeyboardOnScroll));
            items.add(UItem.asCheck(ID_DISABLE_GREETING, "Скрыть приветственный стикер")
                    .setChecked(DevGramConfig.disableGreetingSticker));
            items.add(UItem.asCheck(ID_COMMA_AFTER_MENTION, "Запятая после упоминания")
                    .setChecked(DevGramConfig.addCommaAfterMention));
            items.add(UItem.asCheck(ID_TIME_WITH_SECONDS, "Время с секундами")
                    .setChecked(DevGramConfig.isFormatWithSeconds()));
            items.add(UItem.asCheck(ID_GLASS_MENU, "Стеклянные меню")
                    .setChecked(DevGramConfig.glassMenu));
            items.add(UItem.asCheck(ID_HIDE_EMOJI_CATEGORIES, "Скрыть категории в поиске эмодзи")
                    .setChecked(DevGramConfig.hideEmojiCategories));
            items.add(UItem.asCheck(ID_NUMBER_ROUNDING, "Отключить округление чисел")
                    .setChecked(DevGramConfig.disableNumberRounding));
            items.add(UItem.asShadow("Markdown: символы **жирный** и `код` не превращаются в "
                    + "форматирование автоматически (ручное через меню выделения остаётся)."));
            items.add(UItem.asHeader("Стикеры"));
            items.add(UItem.asIntSlideView(1, 2, (int) DevGramConfig.getStickerSize(), 14,
                    val -> String.valueOf(val),
                    val -> DevGramConfig.setStickerSize(val)).setId(ID_STICKER_SIZE));
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
        } else if (item.id == ID_HIDE_STATUS) {
            gToggle("dg_hideStatus", false);
            if (chatListPreview != null) chatListPreview.invalidate();
            refreshList();
        } else if (item.id == ID_HIDE_STORIES) {
            gToggle("dg_hideStories", false);
        } else if (item.id == ID_HIDE_FAB) {
            gToggle("dg_hideFab", false);
        } else if (item.id == ID_HIDE_SEARCHBAR) {
            gToggle("dg_hideSearchBar", false);
        } else if (item.id == ID_TAB_COUNTER) {
            gToggle("dg_tabCounter", true);
            if (foldersPreview != null) foldersPreview.invalidate();
        } else if (item.id == ID_SYSTEM_FONTS) {
            gToggle("dg_systemFonts", false);
            org.telegram.messenger.AndroidUtilities.invalidateDevgramSystemFonts();
        } else if (item.id == ID_GOOEY) {
            gToggle("dg_gooey", false);
        } else if (item.id == ID_CUSTOM_THEMES) {
            gToggle("dg_customThemes", false);
        } else if (item.id == ID_HIDE_DIVIDERS) {
            gToggle("dg_hideDividers", false);
        } else if (item.id == ID_FULL_DIVIDERS) {
            gToggle("dg_fullDividers", false);
        } else if (item.id == ID_SEPARATE_HEADERS) {
            gToggle("dg_separateHeaders", false);
        } else if (item.id == ID_GLARE) {
            gToggle("dg_glare", true);
        } else if (item.id == ID_MD3) {
            gToggle("dg_md3", true);
        } else if (item.id == ID_MD3_PROGRESS) {
            gToggle("dg_md3_progress", true);
        } else if (item.id == ID_MD3_SLIDER) {
            gToggle("dg_md3_slider", true);
        } else if (item.id == ID_MD3_SWITCH) {
            gToggle("dg_md3_switch", true);
        } else if (item.id == ID_MD3_TITLE) {
            gToggle("dg_md3_title", true);
        } else if (item.id == ID_MD3_BOTTOMNAV) {
            gToggle("dg_md3_bottomnav", false);
        } else if (item.id == ID_CENTER_TITLE) {
            DevGramConfig.setCenterTitle(!DevGramConfig.centerTitle);
            if (chatListPreview != null) chatListPreview.invalidate();
        } else if (item.id == ID_HIDE_ALL_CHATS) {
            gToggle("hideAllChatsTab", false);
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

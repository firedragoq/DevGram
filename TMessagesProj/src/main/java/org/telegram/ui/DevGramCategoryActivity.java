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
    // Чаты
    private static final int ID_DISABLE_MARKDOWN = 22;
    private static final int ID_HIDE_KEYBOARD_ON_SCROLL = 23;
    private static final int ID_DISABLE_GREETING = 24;

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
            // Форма аватара (перенесено из скрытого экрана Fork). Пишем pref avatarCorners,
            // который читает AndroidUtilities.avatarCornersType() по всему приложению.
            items.add(UItem.asHeader("Форма аватара"));
            items.add(UItem.asSlideView(
                    new String[]{"Круг", "Скруглённый", "Квадрат"},
                    org.telegram.messenger.AndroidUtilities.avatarCornersType(),
                    index -> {
                        org.telegram.messenger.MessagesController.getGlobalMainSettings()
                                .edit().putInt("avatarCorners", index).apply();
                    }).setId(ID_AVATAR_SHAPE));
            items.add(UItem.asShadow("Применяется к аватаркам по всему приложению. Открой экран заново, "
                    + "чтобы применить везде."));

            items.add(UItem.asCheck(ID_NUMBER_ROUNDING, "Отключить округление чисел")
                    .setChecked(DevGramConfig.disableNumberRounding));
            items.add(UItem.asShadow("Счётчики (подписчики, просмотры, реакции) будут показываться "
                    + "полностью: 1 234 вместо 1.2K."));

            items.add(UItem.asCheck(ID_SQUARE_FAB, "Квадратная кнопка")
                    .setChecked(DevGramConfig.squareFab));
            items.add(UItem.asShadow("Плавающая кнопка (написать/камера) будет со скруглёнными углами "
                    + "вместо круга. Открой список чатов заново, чтобы применить."));

            items.add(UItem.asCheck(ID_GLASS_MENU, "Стеклянное меню сообщения")
                    .setChecked(DevGramConfig.glassMenu));
            items.add(UItem.asShadow("Меню сообщения становится матовым стеклом — под ним размытый "
                    + "снимок чата. Выключи, если мешает или тормозит."));
        } else if (category == CATEGORY_CHATS) {
            items.add(UItem.asCheck(ID_DISABLE_MARKDOWN, "Отключить Markdown")
                    .setChecked(DevGramConfig.disableMarkdown));
            items.add(UItem.asShadow("Символы **жирный**, `код`, ``` не будут автоматически "
                    + "превращаться в форматирование. Ручное форматирование через меню выделения остаётся."));
            items.add(UItem.asCheck(ID_HIDE_KEYBOARD_ON_SCROLL, "Скрывать клавиатуру при прокрутке")
                    .setChecked(DevGramConfig.hideKeyboardOnScroll));
            items.add(UItem.asShadow("Клавиатура прячется, когда прокручиваешь список сообщений."));
            items.add(UItem.asCheck(ID_DISABLE_GREETING, "Скрыть приветственный стикер")
                    .setChecked(DevGramConfig.disableGreetingSticker));
            items.add(UItem.asShadow("Большой стикер-приветствие в пустом чате показываться не будет."));
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
        } else if (item.id == ID_DISABLE_MARKDOWN) {
            DevGramConfig.setDisableMarkdown(!DevGramConfig.disableMarkdown);
        } else if (item.id == ID_HIDE_KEYBOARD_ON_SCROLL) {
            DevGramConfig.setHideKeyboardOnScroll(!DevGramConfig.hideKeyboardOnScroll);
        } else if (item.id == ID_DISABLE_GREETING) {
            DevGramConfig.setDisableGreetingSticker(!DevGramConfig.disableGreetingSticker);
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

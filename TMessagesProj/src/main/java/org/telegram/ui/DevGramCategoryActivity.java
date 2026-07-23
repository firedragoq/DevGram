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
            items.add(UItem.asShadow("Удалённые остаются в чате с пометкой «🗑» и в списке чатов. "
                    + "У изменённых доступна история правок по тапу на текст. Вложения — фото, видео, "
                    + "кружки, голосовые, стикеры, гифки, файлы — закрепляются отдельно, "
                    + "иначе их вычистит вместе с кешем."));
        } else {
            items.add(UItem.asCheck(ID_SHOW_CONTACTS, "Показывать «Контакты» в нижнем меню")
                    .setChecked(getUserConfig().showContactsTab));
            items.add(UItem.asShadow("Кнопку можно скрыть и долгим нажатием по ней в нижнем меню."));

            items.add(UItem.asCheck(ID_DISABLE_ADS, "Скрывать рекламу")
                    .setChecked(DevGramConfig.disableAds));
            items.add(UItem.asShadow("Убирает спонсорские сообщения в каналах и у ботов, а также рекламу "
                    + "в видеоплеере. Клиент не просто прячет их, а вообще не запрашивает у сервера."));

            items.add(UItem.asCheck(ID_LOCAL_PREMIUM, "Локальный премиум")
                    .setChecked(DevGramConfig.localPremium));
            items.add(UItem.asShadow("Разблокирует клиентские премиум-функции: безлимит папок, "
                    + "премиум-эмодзи и реакции, статусы, увеличенные лимиты. Работает только локально — "
                    + "серверные возможности (гигабайтные загрузки, премиум-реакции у собеседников) недоступны."));
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
        } else if (item.id == ID_SHOW_CONTACTS) {
            getUserConfig().setShowContactsTab(!getUserConfig().showContactsTab);
            NotificationCenter.getInstance(currentAccount).postNotificationName(NotificationCenter.contactsTabVisibleToggled);
        } else {
            return;
        }
        if (listView != null && listView.adapter != null) {
            listView.adapter.update(true);
        }
    }
}

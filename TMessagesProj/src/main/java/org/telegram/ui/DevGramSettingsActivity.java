package org.telegram.ui;

import android.content.Context;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;

import org.telegram.messenger.DevGramConfig;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.UItem;
import org.telegram.ui.Components.UniversalAdapter;
import org.telegram.ui.Components.UniversalRecyclerView;

import java.util.ArrayList;

// DevGram: экран настроек мода (аналог «exteraGram Settings»).
// Пока пустой — сюда будем добавлять собственные опции DevGram.
public class DevGramSettingsActivity extends BaseFragment {

    private UniversalRecyclerView listView;

    @Override
    public View createView(Context context) {
        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setAllowOverlayTitle(true);
        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(int id) {
                if (id == -1) {
                    finishFragment();
                }
            }
        });
        actionBar.setTitle("DevGram");

        FrameLayout contentView = new FrameLayout(context);

        listView = new UniversalRecyclerView(this, this::fillItems, this::onItemClick, null);
        listView.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundGray, resourceProvider));
        contentView.addView(listView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.FILL));
        actionBar.setAdaptiveBackground(listView);

        return fragmentView = contentView;
    }

    // id опций
    private static final int ID_GHOST_MASTER = 1;
    private static final int ID_GHOST_READ = 2;
    private static final int ID_GHOST_ONLINE = 3;
    private static final int ID_GHOST_TYPING = 4;
    private static final int ID_SAVE_DELETED = 5;
    private static final int ID_SAVE_HISTORY = 6;

    private void fillItems(ArrayList<UItem> items, UniversalAdapter adapter) {
        items.add(UItem.asHeader("Режим призрака"));
        items.add(UItem.asCheck(ID_GHOST_MASTER, "Режим призрака").setChecked(DevGramConfig.isGhostModeActive()));
        items.add(UItem.asShadow("Скрывает вашу активность: собеседники не видят прочтение, статус «в сети» и «печатает…»."));

        items.add(UItem.asHeader("Что скрывать"));
        items.add(UItem.asCheck(ID_GHOST_READ, "Не отправлять статус прочтения").setChecked(!DevGramConfig.sendReadPackets));
        items.add(UItem.asCheck(ID_GHOST_ONLINE, "Скрывать статус «в сети»").setChecked(!DevGramConfig.sendOnlinePackets));
        items.add(UItem.asCheck(ID_GHOST_TYPING, "Скрывать «печатает…»").setChecked(!DevGramConfig.sendUploadTyping));
        items.add(UItem.asShadow(null));

        items.add(UItem.asHeader("История сообщений"));
        items.add(UItem.asCheck(ID_SAVE_DELETED, "Сохранять удалённые").setChecked(DevGramConfig.saveDeletedMessages));
        items.add(UItem.asCheck(ID_SAVE_HISTORY, "Сохранять историю изменений").setChecked(DevGramConfig.saveMessagesHistory));
        items.add(UItem.asShadow("Удалённые сообщения остаются в чате с пометкой «🗑». У изменённых доступна история правок по нажатию."));
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
        } else {
            return;
        }
        if (listView != null && listView.adapter != null) {
            listView.adapter.update(true);
        }
    }
}

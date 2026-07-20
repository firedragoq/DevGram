package org.telegram.ui;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
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

// DevGram: экран настроек мода (аналог «exteraGram Settings»): шапка + секции с опциями.
// Только те опции, которые уже реализованы.
public class DevGramSettingsActivity extends BaseFragment {

    private UniversalRecyclerView listView;
    private View headerView;

    // Режим призрака (как у AyuGram): мастер-переключатель + круглые чекбоксы
    private static final int ID_GHOST_MASTER = 1;
    private static final int ID_GHOST_READ = 2;
    private static final int ID_GHOST_ONLINE = 3;
    private static final int ID_GHOST_TYPING = 4;
    // История сообщений
    private static final int ID_SAVE_DELETED = 5;
    private static final int ID_SAVE_HISTORY = 6;
    // Интерфейс
    private static final int ID_SHOW_CONTACTS = 7;

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

        headerView = createHeader(context);

        FrameLayout contentView = new FrameLayout(context);
        listView = new UniversalRecyclerView(this, this::fillItems, this::onItemClick, null);
        listView.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundGray, resourceProvider));
        contentView.addView(listView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.FILL));
        actionBar.setAdaptiveBackground(listView);

        return fragmentView = contentView;
    }

    private View createHeader(Context context) {
        LinearLayout layout = new LinearLayout(context);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setGravity(Gravity.CENTER_HORIZONTAL);
        layout.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite, resourceProvider));
        layout.setPadding(0, AndroidUtilities.dp(18), 0, AndroidUtilities.dp(18));

        ImageView icon = new ImageView(context);
        try {
            icon.setImageDrawable(context.getDrawable(R.mipmap.ic_launcher));
        } catch (Throwable ignore) {
        }
        layout.addView(icon, LayoutHelper.createLinear(76, 76));

        TextView name = new TextView(context);
        name.setText("DevGram");
        name.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText, resourceProvider));
        name.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 20);
        name.setTypeface(AndroidUtilities.bold());
        layout.addView(name, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, 0f, 10f, 0f, 0f));

        TextView ver = new TextView(context);
        ver.setText(getVersionString());
        ver.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText2, resourceProvider));
        ver.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
        layout.addView(ver, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, 0f, 4f, 0f, 0f));

        return layout;
    }

    private String getVersionString() {
        try {
            PackageInfo pInfo = ApplicationLoader.applicationContext.getPackageManager()
                    .getPackageInfo(ApplicationLoader.applicationContext.getPackageName(), 0);
            return pInfo.versionName + " (" + pInfo.versionCode + ")";
        } catch (Throwable e) {
            return "";
        }
    }

    private int ghostCount() {
        int c = 0;
        if (!DevGramConfig.sendReadPackets) c++;
        if (!DevGramConfig.sendOnlinePackets) c++;
        if (!DevGramConfig.sendUploadTyping) c++;
        return c;
    }

    private void fillItems(ArrayList<UItem> items, UniversalAdapter adapter) {
        if (headerView != null) {
            items.add(UItem.asCustom(headerView));
        }

        // --- Режим призрака (мастер-переключатель + круглые чекбоксы, как у AyuGram) ---
        items.add(UItem.asHeader("Режим призрака"));
        items.add(UItem.asCheck(ID_GHOST_MASTER, "Режим призрака  " + ghostCount() + "/3").setChecked(DevGramConfig.isGhostModeActive()));
        items.add(UItem.asRoundCheckbox(ID_GHOST_READ, "Не отправлять прочтение").setChecked(!DevGramConfig.sendReadPackets));
        items.add(UItem.asRoundCheckbox(ID_GHOST_ONLINE, "Не показывать «в сети»").setChecked(!DevGramConfig.sendOnlinePackets));
        items.add(UItem.asRoundCheckbox(ID_GHOST_TYPING, "Не показывать «печатает»").setChecked(!DevGramConfig.sendUploadTyping));
        items.add(UItem.asShadow("Собеседники не видят ваше прочтение, статус «в сети» и «печатает…»."));

        // --- История сообщений ---
        items.add(UItem.asHeader("История сообщений"));
        items.add(UItem.asCheck(ID_SAVE_DELETED, "Сохранять удалённые").setChecked(DevGramConfig.saveDeletedMessages));
        items.add(UItem.asCheck(ID_SAVE_HISTORY, "Сохранять историю изменений").setChecked(DevGramConfig.saveMessagesHistory));
        items.add(UItem.asShadow("Удалённые остаются в чате с пометкой «🗑». У изменённых — история правок по тапу на текст."));

        // --- Интерфейс ---
        items.add(UItem.asHeader("Интерфейс"));
        items.add(UItem.asCheck(ID_SHOW_CONTACTS, "Показывать «Контакты» в нижнем меню").setChecked(getUserConfig().showContactsTab));
        items.add(UItem.asShadow(null));
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

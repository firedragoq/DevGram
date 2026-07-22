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
import org.telegram.messenger.DevGramBadges;
import org.telegram.messenger.ApplicationLoader;
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

    // Категории (каждая открывает свой экран)
    private static final int ID_CAT_GENERAL = 1;
    private static final int ID_CAT_GHOST = 2;
    private static final int ID_CAT_SPY = 3;
    private static final int ID_BADGES = 4; // выдача значков — только для команды

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

    private void fillItems(ArrayList<UItem> items, UniversalAdapter adapter) {
        if (headerView != null) {
            items.add(UItem.asCustom(headerView));
        }

        // Только список категорий — сами опции живут внутри разделов
        items.add(UItem.asHeader("Категории"));
        items.add(UItem.asButton(ID_CAT_GENERAL, R.drawable.devgram_cat_general, "Основные"));
        items.add(UItem.asButton(ID_CAT_GHOST, R.drawable.devgram_cat_ghost, "Режим призрака"));
        items.add(UItem.asButton(ID_CAT_SPY, R.drawable.devgram_cat_spy, "Слежка"));
        items.add(UItem.asShadow(null));

        // Раздел для команды проекта: выдача значков. Видно только участникам команды.
        if (DevGramBadges.isTeam(getUserConfig().getClientUserId())) {
            items.add(UItem.asHeader("Разработчику"));
            items.add(UItem.asButton(ID_BADGES, R.drawable.devgram_supporter, "Значки DevGram"));
            items.add(UItem.asShadow("Выдача значков пользователям и чатам по ID."));
        }
    }

    private void onItemClick(UItem item, View view, int position, float x, float y) {
        if (item.id == ID_CAT_GENERAL) {
            presentFragment(new DevGramCategoryActivity(DevGramCategoryActivity.CATEGORY_GENERAL));
        } else if (item.id == ID_CAT_GHOST) {
            presentFragment(new DevGramCategoryActivity(DevGramCategoryActivity.CATEGORY_GHOST));
        } else if (item.id == ID_CAT_SPY) {
            presentFragment(new DevGramCategoryActivity(DevGramCategoryActivity.CATEGORY_SPY));
        } else if (item.id == ID_BADGES) {
            presentFragment(new DevGramBadgesActivity());
        }
    }
}

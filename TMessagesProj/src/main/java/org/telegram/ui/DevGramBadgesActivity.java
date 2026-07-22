/*
 * DevGram: экран выдачи значков (доступен команде проекта).
 * Разработчик вводит id и выбирает роль — значок сохраняется в SharedPreferences,
 * ничего пересобирать не нужно. Список выданных можно снять по тапу.
 */

package org.telegram.ui;

import android.content.Context;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.widget.EditText;
import android.widget.FrameLayout;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.DevGramBadges;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.messenger.Utilities;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.EditTextBoldCursor;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.UItem;
import org.telegram.ui.Components.UniversalAdapter;
import org.telegram.ui.Components.UniversalRecyclerView;

import java.util.ArrayList;

public class DevGramBadgesActivity extends BaseFragment {

    private static final int ID_ADD = 1;
    private static final int ID_LIST_BASE = 1000; // выданные значки: ID_LIST_BASE + индекс

    private UniversalRecyclerView listView;
    private ArrayList<long[]> granted = new ArrayList<>(); // [dialogId, role]

    @Override
    public View createView(Context context) {
        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setAllowOverlayTitle(true);
        actionBar.setTitle("Значки DevGram");
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

    private void fillItems(ArrayList<UItem> items, UniversalAdapter adapter) {
        items.add(UItem.asButton(ID_ADD, R.drawable.msg_contact_add, "Выдать значок по ID"));
        items.add(UItem.asShadow("Введите ID пользователя (для команды/поддержавших) или ID чата "
                + "(для верификации) и выберите роль. Значок появится у него сразу, без пересборки."));

        granted = DevGramBadges.listGranted();
        if (!granted.isEmpty()) {
            items.add(UItem.asHeader("Выдано вручную"));
            for (int i = 0; i < granted.size(); i++) {
                long[] g = granted.get(i);
                long dialogId = g[0];
                int role = (int) g[1];
                // для чата показываем ID без знака
                long shown = dialogId < 0 ? -dialogId : dialogId;
                items.add(UItem.asButton(ID_LIST_BASE + i, "ID " + shown, DevGramBadges.roleName(role)));
            }
            items.add(UItem.asShadow("Нажмите на строку, чтобы снять значок."));
        }
    }

    private void onItemClick(UItem item, View view, int position, float x, float y) {
        if (item.id == ID_ADD) {
            showGrantDialog();
        } else if (item.id >= ID_LIST_BASE) {
            int idx = item.id - ID_LIST_BASE;
            if (idx >= 0 && idx < granted.size()) {
                showRevokeDialog(granted.get(idx));
            }
        }
    }

    private void showGrantDialog() {
        // шаг 1 — выбор роли
        AlertDialog.Builder roles = new AlertDialog.Builder(getParentActivity());
        roles.setTitle("Кому выдать значок?");
        String[] names = {
                DevGramBadges.roleName(DevGramBadges.ROLE_TEAM),
                DevGramBadges.roleName(DevGramBadges.ROLE_SUPPORTER),
                DevGramBadges.roleName(DevGramBadges.ROLE_OFFICIAL),
        };
        int[] values = {DevGramBadges.ROLE_TEAM, DevGramBadges.ROLE_SUPPORTER, DevGramBadges.ROLE_OFFICIAL};
        roles.setItems(names, (dialog, which) -> askId(values[which]));
        roles.setNegativeButton(LocaleController.getString(R.string.Cancel), null);
        showDialog(roles.create());
    }

    private void askId(int role) {
        Context context = getParentActivity();
        EditTextBoldCursor editText = new EditTextBoldCursor(context);
        editText.setTextSize(android.util.TypedValue.COMPLEX_UNIT_DIP, 18);
        editText.setTextColor(Theme.getColor(Theme.key_dialogTextBlack, resourceProvider));
        editText.setInputType(InputType.TYPE_CLASS_NUMBER);
        editText.setHint(role == DevGramBadges.ROLE_OFFICIAL ? "ID чата" : "ID пользователя");
        editText.setHintColor(Theme.getColor(Theme.key_dialogTextHint, resourceProvider));
        editText.setCursorColor(Theme.getColor(Theme.key_dialogTextBlack, resourceProvider));
        editText.setCursorSize(AndroidUtilities.dp(20));
        editText.setCursorWidth(1.5f);
        editText.setBackgroundDrawable(Theme.createEditTextDrawable(context, true));

        // AlertDialog кладёт customView в LinearLayout, поэтому отступы задаём через
        // собственный контейнер, а не через cast LayoutParams (иначе ClassCastException).
        FrameLayout container = new FrameLayout(context);
        container.setPadding(AndroidUtilities.dp(24), AndroidUtilities.dp(4), AndroidUtilities.dp(24), 0);
        container.addView(editText, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 44));

        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setTitle(DevGramBadges.roleName(role));
        builder.setView(container);
        builder.setPositiveButton("Выдать", (d, w) -> {
            long id = Utilities.parseLong(editText.getText().toString());
            if (id != 0) {
                DevGramBadges.grant(id, role);
                update();
            }
        });
        builder.setNegativeButton(LocaleController.getString(R.string.Cancel), null);
        showDialog(builder.create());
        editText.requestFocus();
        AndroidUtilities.showKeyboard(editText);
    }

    private void showRevokeDialog(long[] g) {
        long dialogId = g[0];
        long shown = dialogId < 0 ? -dialogId : dialogId;
        AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
        builder.setTitle("Снять значок");
        builder.setMessage("Убрать значок «" + DevGramBadges.roleName((int) g[1]) + "» у ID " + shown + "?");
        builder.setPositiveButton("Снять", (d, w) -> {
            DevGramBadges.revoke(dialogId);
            update();
        });
        builder.setNegativeButton(LocaleController.getString(R.string.Cancel), null);
        showDialog(builder.create());
    }

    private void update() {
        if (listView != null && listView.adapter != null) {
            listView.adapter.update(true);
        }
        // перерисовать списки чатов/профили, чтобы значок появился/пропал сразу
        org.telegram.messenger.NotificationCenter.getGlobalInstance()
                .postNotificationName(org.telegram.messenger.NotificationCenter.reloadInterface);
    }
}

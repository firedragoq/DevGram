/*
 * DevGram: экран истории изменений сообщения.
 * Показывает сохранённые предыдущие версии (ревизии) + текущую.
 * Данные берутся из DevGramMessagesController (BLOB сериализованного TLRPC.Message).
 */

package org.telegram.ui;

import android.content.Context;
import android.graphics.Typeface;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.DevGramMessagesController;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.LayoutHelper;

import java.util.List;

public class DevGramMessageHistoryActivity extends BaseFragment {

    private final long dialogId;
    private final int messageId;
    private final CharSequence currentText;

    public DevGramMessageHistoryActivity(long dialogId, int messageId, CharSequence currentText) {
        this.dialogId = dialogId;
        this.messageId = messageId;
        this.currentText = currentText;
    }

    @Override
    public View createView(Context context) {
        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setAllowOverlayTitle(true);
        actionBar.setTitle("История изменений");
        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(int id) {
                if (id == -1) {
                    finishFragment();
                }
            }
        });

        ScrollView scrollView = new ScrollView(context);
        scrollView.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundGray, resourceProvider));

        LinearLayout container = new LinearLayout(context);
        container.setOrientation(LinearLayout.VERTICAL);
        container.setPadding(0, AndroidUtilities.dp(8), 0, AndroidUtilities.dp(8));
        scrollView.addView(container, LayoutHelper.createScroll(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.TOP));

        long selfId = getUserConfig().getClientUserId();
        List<TLRPC.Message> revisions = DevGramMessagesController.getInstance().getRevisions(selfId, dialogId, messageId);

        int version = 1;
        for (TLRPC.Message rev : revisions) {
            int date = rev.edit_date != 0 ? rev.edit_date : rev.date;
            CharSequence text = rev.message;
            if (TextUtils.isEmpty(text)) {
                text = rev.media != null ? "[медиа]" : "[пустое сообщение]";
            }
            addCard(context, container, "Версия " + version, date, text);
            version++;
        }

        // текущая (действующая) версия
        CharSequence cur = currentText;
        if (TextUtils.isEmpty(cur)) {
            cur = "[медиа или пустое сообщение]";
        }
        addCard(context, container, "Текущая версия", 0, cur);

        fragmentView = scrollView;
        return fragmentView;
    }

    private void addCard(Context context, LinearLayout container, String label, int dateSeconds, CharSequence text) {
        LinearLayout card = new LinearLayout(context);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite, resourceProvider));
        card.setPadding(AndroidUtilities.dp(16), AndroidUtilities.dp(12), AndroidUtilities.dp(16), AndroidUtilities.dp(12));

        TextView header = new TextView(context);
        header.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText2, resourceProvider));
        header.setTextSize(13);
        header.setTypeface(AndroidUtilities.bold());
        String headerText = label;
        if (dateSeconds != 0) {
            headerText += " · " + LocaleController.formatDateTime(dateSeconds, true);
        }
        header.setText(headerText);
        card.addView(header, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT));

        TextView body = new TextView(context);
        body.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText, resourceProvider));
        body.setTextSize(16);
        body.setText(text);
        card.addView(body, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 6, 0, 0));

        LinearLayout.LayoutParams lp = LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT);
        lp.topMargin = AndroidUtilities.dp(8);
        container.addView(card, lp);
    }
}

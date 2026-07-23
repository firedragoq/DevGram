/*
 * DevGram: экран «История удалёнок» — все сохранённые удалённые сообщения чата,
 * отрисованные настоящими бабблами (ChatMessageCell) на фоне-обоях, как мини-чат.
 */

package org.telegram.ui;

import android.content.Context;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;

import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ContactsController;
import org.telegram.messenger.DevGramMessagesController;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.R;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.ChatMessageCell;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.RecyclerListView;
import org.telegram.ui.Components.SizeNotifierFrameLayout;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class DevGramDeletedHistoryActivity extends BaseFragment {

    private final long dialogId;
    private final long topicId;
    private final ArrayList<MessageObject> messages = new ArrayList<>();
    private RecyclerListView listView;

    public DevGramDeletedHistoryActivity(long dialogId, long topicId) {
        this.dialogId = dialogId;
        this.topicId = topicId;
    }

    @Override
    public boolean onFragmentCreate() {
        loadMessages();
        return super.onFragmentCreate();
    }

    private void loadMessages() {
        long selfId = getUserConfig().getClientUserId();
        List<TLRPC.Message> saved = DevGramMessagesController.getInstance().getDeletedMessages(selfId, dialogId, topicId);
        Collections.sort(saved, (a, b) -> Integer.compare(a.id, b.id)); // от старых к новым
        for (TLRPC.Message m : saved) {
            if (m == null) {
                continue;
            }
            try {
                MessageObject mo = new MessageObject(currentAccount, m, true, true);
                // входящие рисуем с аватаркой отправителя (в т.ч. в личке), чтобы всегда было
                // видно, кто прислал удалённое сообщение; свои — без аватарки, как в обычном чате
                if (!mo.isOutOwner()) {
                    mo.forceAvatar = true;
                }
                messages.add(mo);
            } catch (Throwable ignore) {
            }
        }
    }

    private String getChatTitle() {
        if (dialogId > 0) {
            TLRPC.User u = getMessagesController().getUser(dialogId);
            if (u != null) {
                return ContactsController.formatName(u.first_name, u.last_name);
            }
        } else {
            TLRPC.Chat c = getMessagesController().getChat(-dialogId);
            if (c != null) {
                return c.title;
            }
        }
        return "Удалённые";
    }

    @Override
    public View createView(Context context) {
        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setAllowOverlayTitle(true);
        actionBar.setTitle(getChatTitle());
        actionBar.setSubtitle(messages.size() + " " + pluralMessages(messages.size()));
        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(int id) {
                if (id == -1) {
                    finishFragment();
                }
            }
        });

        // isActionBarVisible()/useRootView() переопределены в false: наш actionBar лежит НАД
        // contentView (обычный fragment-layout), поэтому обои НЕ надо повторно сдвигать и клипать
        // на высоту экшн-бара — иначе сверху чёрная полоса высотой в экшн-бар.
        SizeNotifierFrameLayout contentView = new SizeNotifierFrameLayout(context) {
            @Override
            protected boolean isActionBarVisible() {
                return false;
            }

            @Override
            protected boolean useRootView() {
                return false;
            }
        };
        contentView.setOccupyStatusBar(false);
        contentView.setBackgroundImage(Theme.getCachedWallpaper(), Theme.isWallpaperMotion());

        listView = new RecyclerListView(context);
        // как в настоящем чате: сообщения прижаты книзу, при открытии видно самые свежие,
        // вверх листаем к более старым (обои теперь заполняют весь фон, пустоты сверху нет)
        LinearLayoutManager layoutManager = new LinearLayoutManager(context);
        layoutManager.setStackFromEnd(true);
        listView.setLayoutManager(layoutManager);
        listView.setAdapter(new Adapter());
        listView.setVerticalScrollBarEnabled(false);
        listView.setPadding(0, AndroidUtilities.dp(8), 0, AndroidUtilities.dp(8));
        listView.setClipToPadding(false);
        contentView.addView(listView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.FILL));

        if (messages.isEmpty()) {
            android.widget.TextView empty = new android.widget.TextView(context);
            empty.setText("Здесь пока нет сохранённых удалённых сообщений");
            empty.setGravity(Gravity.CENTER);
            empty.setTextColor(Theme.getColor(Theme.key_chat_serviceText, resourceProvider));
            empty.setTextSize(android.util.TypedValue.COMPLEX_UNIT_DIP, 15);
            empty.setPadding(org.telegram.messenger.AndroidUtilities.dp(32), 0, org.telegram.messenger.AndroidUtilities.dp(32), 0);
            contentView.addView(empty, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER));
        }

        fragmentView = contentView;
        return fragmentView;
    }

    private String pluralMessages(int n) {
        int mod10 = n % 10;
        int mod100 = n % 100;
        if (mod10 == 1 && mod100 != 11) return "сообщение";
        if (mod10 >= 2 && mod10 <= 4 && (mod100 < 12 || mod100 > 14)) return "сообщения";
        return "сообщений";
    }

    private class Adapter extends RecyclerListView.SelectionAdapter {
        @Override
        public boolean isEnabled(RecyclerView.ViewHolder holder) {
            return false;
        }

        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            ChatMessageCell cell = new ChatMessageCell(parent.getContext(), currentAccount);
            cell.setDelegate(new ChatMessageCell.ChatMessageCellDelegate() {
            });
            cell.setLayoutParams(new RecyclerView.LayoutParams(RecyclerView.LayoutParams.MATCH_PARENT, RecyclerView.LayoutParams.WRAP_CONTENT));
            return new RecyclerListView.Holder(cell);
        }

        @Override
        public void onBindViewHolder(RecyclerView.ViewHolder holder, int position) {
            ChatMessageCell cell = (ChatMessageCell) holder.itemView;
            MessageObject message = messages.get(position);
            // ВАЖНО: без isChat=true ячейка не рисует ни аватарку, ни имя отправителя
            // (needDrawAvatar() требует именно этот флаг)
            cell.isChat = true;
            cell.setFullyDraw(true);
            // без группировки: у КАЖДОГО сообщения своя аватарка и имя отправителя,
            // чтобы всегда было понятно, кто прислал удалённое сообщение
            cell.setMessageObject(message, null, false, false, position == 0);
        }

        @Override
        public int getItemCount() {
            return messages.size();
        }
    }
}

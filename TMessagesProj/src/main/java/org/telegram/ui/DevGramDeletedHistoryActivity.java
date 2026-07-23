/*
 * DevGram: экран «История удалёнок» — все сохранённые удалённые сообщения чата,
 * отрисованные настоящими бабблами (ChatMessageCell) на фоне-обоях, как мини-чат.
 * По тапу на сообщение — меню (Копировать / Показать в чате / Детали / Удалить),
 * как в AyuGram. Медиа грузится из локально сохранённой копии (attachPath).
 */

package org.telegram.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.GestureDetector;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;

import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ContactsController;
import org.telegram.messenger.DevGramMessagesController;
import org.telegram.messenger.ImageReceiver;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.R;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.BottomSheet;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.ChatMessageCell;
import org.telegram.ui.Components.BulletinFactory;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.RecyclerListView;
import org.telegram.ui.Components.SizeNotifierFrameLayout;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class DevGramDeletedHistoryActivity extends BaseFragment {

    private final long dialogId;
    private final long topicId;
    private final ArrayList<MessageObject> messages = new ArrayList<>();
    private final ArrayList<Integer> catchTimes = new ArrayList<>(); // дата удаления, синхронно с messages
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
        messages.clear();
        catchTimes.clear();
        long selfId = getUserConfig().getClientUserId();
        List<DevGramMessagesController.DeletedEntry> saved =
                DevGramMessagesController.getInstance().getDeletedEntries(selfId, dialogId, topicId);
        // от старых к новым
        Collections.sort(saved, (a, b) -> Integer.compare(a.message.id, b.message.id));
        for (DevGramMessagesController.DeletedEntry entry : saved) {
            TLRPC.Message m = entry.message;
            if (m == null) {
                continue;
            }
            try {
                // в личке у входящего from_id может отсутствовать — подставляем собеседника,
                // чтобы зарезолвились аватарка и имя отправителя
                if (dialogId > 0 && !m.out && MessageObject.getPeerId(m.from_id) == 0) {
                    TLRPC.TL_peerUser p = new TLRPC.TL_peerUser();
                    p.user_id = dialogId;
                    m.from_id = p;
                }
                MessageObject mo = new MessageObject(currentAccount, m, true, true);
                if (!mo.isOutOwner()) {
                    mo.forceAvatar = true; // всегда показываем, кто прислал
                }
                messages.add(mo);
                catchTimes.add(entry.catchTime);
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

        // isActionBarVisible()/useRootView() → false: actionBar лежит НАД contentView, поэтому
        // обои НЕ надо повторно сдвигать/клипать на высоту экшн-бара (иначе чёрная полоса сверху).
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
        // вверх листаем к более старым
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
            empty.setPadding(AndroidUtilities.dp(32), 0, AndroidUtilities.dp(32), 0);
            contentView.addView(empty, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER));
        }

        fragmentView = contentView;
        return fragmentView;
    }

    // ================= меню по тапу =================

    private void showMessageMenu(MessageObject message, int catchTime) {
        if (getParentActivity() == null || message == null) {
            return;
        }
        final CharSequence text = message.messageOwner != null ? message.messageOwner.message : null;
        final boolean hasText = !TextUtils.isEmpty(text);

        ArrayList<CharSequence> titles = new ArrayList<>();
        ArrayList<Integer> icons = new ArrayList<>();
        ArrayList<Integer> actions = new ArrayList<>();
        if (hasText) {
            titles.add("Копировать");
            icons.add(R.drawable.msg_copy);
            actions.add(0);
        }
        titles.add("Показать в чате");
        icons.add(R.drawable.msg_openin);
        actions.add(1);
        titles.add("Детали");
        icons.add(R.drawable.msg_info);
        actions.add(2);
        titles.add("Удалить");
        icons.add(R.drawable.msg_delete);
        actions.add(3);

        int[] iconsArr = new int[icons.size()];
        for (int i = 0; i < icons.size(); i++) {
            iconsArr[i] = icons.get(i);
        }

        BottomSheet.Builder builder = new BottomSheet.Builder(getParentActivity());
        builder.setItems(titles.toArray(new CharSequence[0]), iconsArr, (dialog, which) -> {
            int action = actions.get(which);
            switch (action) {
                case 0:
                    AndroidUtilities.addToClipboard(text);
                    BulletinFactory.of(this).createCopyBulletin(LocaleController.getString(R.string.TextCopied)).show();
                    break;
                case 1:
                    openInChat(message.getId());
                    break;
                case 2:
                    showDetails(message, catchTime);
                    break;
                case 3:
                    removeEntry(message);
                    break;
            }
        });
        builder.show();
    }

    private void openInChat(int messageId) {
        Bundle args = new Bundle();
        if (dialogId > 0) {
            args.putLong("user_id", dialogId);
        } else {
            args.putLong("chat_id", -dialogId);
        }
        args.putInt("message_id", messageId);
        presentFragment(new ChatActivity(args));
    }

    private void showDetails(MessageObject message, int catchTime) {
        if (getParentActivity() == null) {
            return;
        }
        StringBuilder sb = new StringBuilder();
        sb.append("ID: ").append(message.getId()).append('\n');
        sb.append("Дата: ").append(LocaleController.formatDateTime(message.messageOwner.date, true)).append('\n');
        sb.append("Дата удаления: ").append(catchTime != 0 ? LocaleController.formatDateTime(catchTime, true) : "—");

        AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
        builder.setTitle("Детали");
        builder.setMessage(sb.toString());
        builder.setPositiveButton(LocaleController.getString(R.string.OK), null);
        showDialog(builder.create());
    }

    private void removeEntry(MessageObject message) {
        long selfId = getUserConfig().getClientUserId();
        DevGramMessagesController.getInstance().deleteDeletedMessage(selfId, dialogId, message.getId());
        int idx = messages.indexOf(message);
        if (idx >= 0) {
            messages.remove(idx);
            if (idx < catchTimes.size()) {
                catchTimes.remove(idx);
            }
        }
        if (listView != null && listView.getAdapter() != null) {
            listView.getAdapter().notifyDataSetChanged();
        }
        actionBar.setSubtitle(messages.size() + " " + pluralMessages(messages.size()));
    }

    private String pluralMessages(int n) {
        int mod10 = n % 10;
        int mod100 = n % 100;
        if (mod10 == 1 && mod100 != 11) return "сообщение";
        if (mod10 >= 2 && mod10 <= 4 && (mod100 < 12 || mod100 > 14)) return "сообщения";
        return "сообщений";
    }

    // Ячейка удалёнки:
    //  • аватарку входящих рисуем сами — в обычном чате её рисует ChatActivity внешним
    //    проходом (getAvatarImage()), в standalone-списке этого нет, поэтому onDraw;
    //  • медиа грузим из локально сохранённого файла (attachPath) — иначе битое превью;
    //  • тап ловим GestureDetector'ом: обычный onClick ChatMessageCell съедает.
    private static class DeletedMessageCell extends ChatMessageCell {
        Runnable onTap;
        private final GestureDetector gestureDetector;

        DeletedMessageCell(Context context, int account) {
            super(context, account);
            gestureDetector = new GestureDetector(context, new GestureDetector.SimpleOnGestureListener() {
                @Override
                public boolean onSingleTapUp(MotionEvent e) {
                    if (onTap != null) {
                        onTap.run();
                    }
                    return true;
                }
            });
        }

        @Override
        public boolean onTouchEvent(MotionEvent event) {
            // read-only просмотр: любые внутренние нажатия ячейки нам не нужны, ловим только тап.
            // Скролл продолжает работать: RecyclerView перехватывает MOVE через onInterceptTouchEvent.
            gestureDetector.onTouchEvent(event);
            return true;
        }

        @Override
        protected void onLayout(boolean changed, int l, int t, int r, int b) {
            super.onLayout(changed, l, t, r, b);
            MessageObject mo = getMessageObject();
            if (mo != null && mo.messageOwner != null && !TextUtils.isEmpty(mo.messageOwner.attachPath)) {
                File f = new File(mo.messageOwner.attachPath);
                if (f.exists() && f.length() > 0) {
                    getPhotoImage().setImage(mo.messageOwner.attachPath, null, null, null, 0);
                }
            }
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            if (isAvatarVisible) {
                ImageReceiver a = getAvatarImage();
                if (a != null) {
                    int sz = AndroidUtilities.dp(42);
                    int y = getMeasuredHeight() - sz - AndroidUtilities.dp(2); // низ бабла, как в чате
                    a.setImageCoords(AndroidUtilities.dp(6), y, sz, sz);
                    a.setVisible(true, false);
                    a.draw(canvas);
                }
            }
        }
    }

    private class Adapter extends RecyclerListView.SelectionAdapter {
        @Override
        public boolean isEnabled(RecyclerView.ViewHolder holder) {
            return false;
        }

        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            DeletedMessageCell cell = new DeletedMessageCell(parent.getContext(), currentAccount);
            cell.setDelegate(new ChatMessageCell.ChatMessageCellDelegate() {
            });
            cell.setLayoutParams(new RecyclerView.LayoutParams(RecyclerView.LayoutParams.MATCH_PARENT, RecyclerView.LayoutParams.WRAP_CONTENT));
            return new RecyclerListView.Holder(cell);
        }

        @Override
        public void onBindViewHolder(RecyclerView.ViewHolder holder, int position) {
            DeletedMessageCell cell = (DeletedMessageCell) holder.itemView;
            final MessageObject message = messages.get(position);
            final int catchTime = position < catchTimes.size() ? catchTimes.get(position) : 0;
            // ВАЖНО: isChat=true — иначе не рисуется ни аватарка, ни имя отправителя
            cell.isChat = true;
            cell.setFullyDraw(true);
            cell.setMessageObject(message, null, false, false, position == 0);
            cell.onTap = () -> showMessageMenu(message, catchTime);
        }

        @Override
        public int getItemCount() {
            return messages.size();
        }
    }
}

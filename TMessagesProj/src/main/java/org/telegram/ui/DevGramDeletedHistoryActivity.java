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
import org.telegram.ui.ActionBar.ActionBarMenuSubItem;
import org.telegram.ui.ActionBar.ActionBarPopupWindow;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;
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

    // ================= меню по тапу (как обычное контекстное меню сообщения) =================

    private void showMessageMenu(View anchor, MessageObject message, int catchTime, float tapX, float tapY) {
        if (getParentActivity() == null || message == null || fragmentView == null) {
            return;
        }
        final CharSequence text = message.messageOwner != null ? message.messageOwner.message : null;
        final boolean hasText = !TextUtils.isEmpty(text);

        ActionBarPopupWindow.ActionBarPopupWindowLayout layout =
                new ActionBarPopupWindow.ActionBarPopupWindowLayout(getParentActivity(), R.drawable.popup_fixed_alert2, resourceProvider);
        layout.setFitItems(true);

        final ActionBarPopupWindow[] popup = new ActionBarPopupWindow[1];

        addMenuItem(layout, "Ответить", R.drawable.menu_reply, popup, () -> replyInChat(message));
        addMenuItem(layout, "Переслать", R.drawable.msg_forward, popup, () -> forwardMessage(message));
        if (hasText) {
            addMenuItem(layout, "Копировать", R.drawable.msg_copy, popup, () -> {
                AndroidUtilities.addToClipboard(text);
                BulletinFactory.of(this).createCopyBulletin(LocaleController.getString(R.string.TextCopied)).show();
            });
        }
        addMenuItem(layout, "Показать в чате", R.drawable.msg_openin, popup, () -> openInChat(message.getId()));
        addMenuItem(layout, "Детали", R.drawable.msg_info, popup, () -> showDetails(message, catchTime));
        addMenuItem(layout, "Удалить", R.drawable.msg_delete, popup, () -> removeEntry(message));

        popup[0] = new ActionBarPopupWindow(layout, LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT);
        popup[0].setPauseNotifications(true);
        popup[0].setDismissAnimationDuration(220);
        popup[0].setOutsideTouchable(true);
        popup[0].setClippingEnabled(true);
        popup[0].setAnimationStyle(R.style.PopupContextAnimation);
        popup[0].setFocusable(true);
        layout.measure(
                View.MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(1000), View.MeasureSpec.AT_MOST),
                View.MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(1000), View.MeasureSpec.AT_MOST));
        popup[0].setInputMethodMode(ActionBarPopupWindow.INPUT_METHOD_NOT_NEEDED);
        popup[0].setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_STATE_UNSPECIFIED);

        // позиция — в точку тапа, с зажимом по краям экрана
        int[] loc = new int[2];
        anchor.getLocationInWindow(loc);
        int menuW = layout.getMeasuredWidth();
        int menuH = layout.getMeasuredHeight();
        int x = loc[0] + (int) tapX;
        int y = loc[1] + (int) tapY;
        x = Math.max(AndroidUtilities.dp(8), Math.min(x, AndroidUtilities.displaySize.x - menuW - AndroidUtilities.dp(8)));
        int maxY = AndroidUtilities.displaySize.y - menuH - AndroidUtilities.dp(8);
        if (y > maxY) {
            y = Math.max(AndroidUtilities.dp(8), maxY);
        }
        popup[0].showAtLocation(fragmentView, Gravity.LEFT | Gravity.TOP, x, y);
    }

    private void addMenuItem(ActionBarPopupWindow.ActionBarPopupWindowLayout layout, String text, int icon,
                             ActionBarPopupWindow[] popup, Runnable action) {
        ActionBarMenuSubItem item = new ActionBarMenuSubItem(getParentActivity(), false, false, resourceProvider);
        item.setTextAndIcon(text, icon);
        item.setOnClickListener(v -> {
            if (popup[0] != null) {
                popup[0].dismiss();
            }
            action.run();
        });
        layout.addView(item, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 48));
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

    // Ответить: открываем реальный чат и ставим ответ на это сообщение. Удалённый текст
    // делаем ЦИТАТОЙ (showFieldPanelForReplyQuote) — иначе панель ответа показывает
    // серверную версию сообщения (уже удалённую/пустую). То же действие — по свайпу влево.
    private void replyInChat(MessageObject message) {
        Bundle args = new Bundle();
        if (dialogId > 0) {
            args.putLong("user_id", dialogId);
        } else {
            args.putLong("chat_id", -dialogId);
        }
        ChatActivity chat = new ChatActivity(args);
        presentFragment(chat);
        // текстовую удалёнку цитируем целиком; медиа/пустую — обычным ответом
        ChatActivity.ReplyQuote quote = null;
        if (message.messageOwner != null && !TextUtils.isEmpty(message.messageOwner.message)) {
            quote = ChatActivity.ReplyQuote.from(message);
        }
        final ChatActivity.ReplyQuote fquote = quote;
        // поле ввода создаётся в createView — ставим ответ чуть позже, чтобы оно успело появиться
        AndroidUtilities.runOnUIThread(() -> {
            if (fquote != null) {
                chat.showFieldPanelForReplyQuote(message, fquote);
            } else {
                chat.showFieldPanelForReply(message);
            }
        }, 350);
    }

    // Переслать: стандартный пикер пересылки.
    private void forwardMessage(MessageObject message) {
        Bundle args = new Bundle();
        args.putBoolean("onlySelect", true);
        args.putInt("dialogsType", DialogsActivity.DIALOGS_TYPE_FORWARD);
        DialogsActivity fragment = new DialogsActivity(args);
        fragment.setDelegate((fragment1, dids, m, param, notify, scheduleDate, scheduleRepeatPeriod, topicsFragment) -> {
            if (dids.isEmpty()) {
                return false;
            }
            long did = dids.get(0).dialogId;
            ArrayList<MessageObject> fmsg = new ArrayList<>();
            fmsg.add(message);
            org.telegram.messenger.SendMessagesHelper.getInstance(currentAccount)
                    .sendMessage(fmsg, did, false, false, true, 0, 0);
            Bundle a = new Bundle();
            if (did > 0) {
                a.putLong("user_id", did);
            } else {
                a.putLong("chat_id", -did);
            }
            presentFragment(new ChatActivity(a), true);
            return true;
        });
        presentFragment(fragment);
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
    interface OnCellTap {
        void onTap(DeletedMessageCell cell, float x, float y);
    }

    private static class DeletedMessageCell extends ChatMessageCell {
        OnCellTap onTap;
        Runnable onSwipeReply;
        private final GestureDetector gestureDetector;

        DeletedMessageCell(Context context, int account) {
            super(context, account);
            gestureDetector = new GestureDetector(context, new GestureDetector.SimpleOnGestureListener() {
                @Override
                public boolean onSingleTapUp(MotionEvent e) {
                    if (onTap != null) {
                        onTap.onTap(DeletedMessageCell.this, e.getX(), e.getY());
                    }
                    return true;
                }

                @Override
                public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY) {
                    // смахивание влево (как обычный ответ) → ответить на удалённое сообщение
                    if (onSwipeReply != null && velocityX < -AndroidUtilities.dp(600)
                            && Math.abs(velocityX) > Math.abs(velocityY) * 1.5f) {
                        onSwipeReply.run();
                        return true;
                    }
                    return false;
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
            cell.onTap = (c, x, y) -> showMessageMenu(c, message, catchTime, x, y);
            cell.onSwipeReply = () -> replyInChat(message);
        }

        @Override
        public int getItemCount() {
            return messages.size();
        }
    }
}

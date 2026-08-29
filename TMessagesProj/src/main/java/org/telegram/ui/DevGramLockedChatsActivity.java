package org.telegram.ui;

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.text.InputType;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.biometric.BiometricManager;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ContactsController;
import org.telegram.messenger.DevGramLockedChats;
import org.telegram.messenger.DialogObject;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.UserObject;
import org.telegram.tgnet.TLObject;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.BulletinFactory;
import org.telegram.ui.Components.EditTextBoldCursor;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.UItem;
import org.telegram.ui.Components.UniversalAdapter;
import org.telegram.ui.Components.UniversalRecyclerView;

import java.util.ArrayList;

// DevGram: экран управления скрытыми (запароленными) чатами — пасскод, биометрия,
// уведомления и список скрытых чатов. Открывается только после разблокировки.
public class DevGramLockedChatsActivity extends BaseFragment {

    private static final int ID_SET_PASSCODE = 1;
    private static final int ID_REMOVE_PASSCODE = 2;
    private static final int ID_BIOMETRIC = 3;
    private static final int ID_HIDE_NOTIFY = 4;
    private static final int ID_REVEAL = 5;
    private static final int ID_CHAT_BASE = 1000;

    private UniversalRecyclerView listView;
    private ArrayList<Long> chatIds = new ArrayList<>();

    @Override
    public View createView(Context context) {
        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setAllowOverlayTitle(true);
        actionBar.setTitle(LocaleController.getString(R.string.DevGramLockedChats));
        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(int id) {
                if (id == -1) finishFragment();
            }
        });

        FrameLayout content = new FrameLayout(context);
        listView = new UniversalRecyclerView(this, this::fillItems, this::onItemClick, null);
        listView.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundGray, resourceProvider));
        content.addView(listView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.FILL));
        actionBar.setAdaptiveBackground(listView);
        return fragmentView = content;
    }

    @Override
    public boolean onFragmentCreate() {
        // экран показывает скрытые чаты — на выходе снова прячем
        return super.onFragmentCreate();
    }

    private boolean biometricAvailable() {
        try {
            return BiometricManager.from(getContext()).canAuthenticate(
                    BiometricManager.Authenticators.BIOMETRIC_WEAK) == BiometricManager.BIOMETRIC_SUCCESS;
        } catch (Throwable t) {
            return false;
        }
    }

    private void fillItems(ArrayList<UItem> items, UniversalAdapter adapter) {
        boolean hasPass = DevGramLockedChats.hasPasscode();

        // — красивая «шапка» с иконкой замка
        items.add(UItem.asCustom(makeHero(getContext())));

        items.add(UItem.asShadow(null));
        items.add(UItem.asGraySection(LocaleController.getString(R.string.DevGramLockedChatsPasscode)));
        items.add(UItem.asButton(ID_SET_PASSCODE, R.drawable.outline_header_lock_24, hasPass
                ? LocaleController.getString(R.string.DevGramLockedChatsChangePasscode)
                : LocaleController.getString(R.string.DevGramLockedChatsSetPasscode)));
        if (hasPass) {
            items.add(UItem.asButton(ID_REMOVE_PASSCODE, R.drawable.msg_delete, LocaleController.getString(R.string.DevGramLockedChatsRemovePasscode)).red());
        }
        if (biometricAvailable()) {
            items.add(UItem.asCheck(ID_BIOMETRIC, LocaleController.getString(R.string.DevGramLockedChatsBiometric))
                    .setChecked(DevGramLockedChats.biometricEnabled()));
        }
        items.add(UItem.asCheck(ID_HIDE_NOTIFY, LocaleController.getString(R.string.DevGramLockedChatsHideNotify))
                .setChecked(DevGramLockedChats.hideNotifications()));
        items.add(UItem.asShadow(LocaleController.getString(R.string.DevGramLockedChatsHideNotifyInfo)));

        chatIds = DevGramLockedChats.getAll(currentAccount);
        String sectionTitle = LocaleController.getString(R.string.DevGramLockedChatsList)
                + (chatIds.isEmpty() ? "" : "  •  " + chatIds.size());
        items.add(UItem.asGraySection(sectionTitle));
        items.add(UItem.asCheck(ID_REVEAL, LocaleController.getString(R.string.DevGramLockedChatsReveal))
                .setChecked(DevGramLockedChats.isRevealed()));

        if (chatIds.isEmpty()) {
            items.add(UItem.asShadow(LocaleController.getString(R.string.DevGramLockedChatsEmpty)));
        } else {
            for (int i = 0; i < chatIds.size(); i++) {
                long did = chatIds.get(i);
                TLObject obj = dialogObject(did);
                if (obj != null) {
                    UItem it = UItem.asProfileCell(obj);
                    it.id = ID_CHAT_BASE + i;
                    it.subtext = "🔒 " + LocaleController.getString(R.string.DevGramLockedChatsHidden);
                    items.add(it);
                } else {
                    items.add(UItem.asButton(ID_CHAT_BASE + i, dialogTitle(did)));
                }
            }
            items.add(UItem.asShadow(LocaleController.getString(R.string.DevGramLockedChatsInfo)));
        }
    }

    private TLObject dialogObject(long did) {
        try {
            if (DialogObject.isEncryptedDialog(did)) return null;
            if (DialogObject.isUserDialog(did)) return getMessagesController().getUser(did);
            return getMessagesController().getChat(-did);
        } catch (Throwable t) {
            return null;
        }
    }

    // Красивая «шапка»: замок в цветном круге + заголовок + подпись.
    private View makeHero(Context ctx) {
        LinearLayout ll = new LinearLayout(ctx);
        ll.setOrientation(LinearLayout.VERTICAL);
        ll.setGravity(Gravity.CENTER_HORIZONTAL);
        ll.setPadding(AndroidUtilities.dp(20), AndroidUtilities.dp(20), AndroidUtilities.dp(20), AndroidUtilities.dp(8));
        ll.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite, resourceProvider));

        int accent = Theme.getColor(Theme.key_featuredStickers_addButton, resourceProvider);
        FrameLayout circle = new FrameLayout(ctx);
        GradientDrawable bg = new GradientDrawable();
        bg.setShape(GradientDrawable.OVAL);
        bg.setColor(Theme.multAlpha(accent, 0.14f));
        circle.setBackground(bg);
        ImageView icon = new ImageView(ctx);
        icon.setImageResource(R.drawable.msg_secret);
        icon.setColorFilter(new android.graphics.PorterDuffColorFilter(accent, android.graphics.PorterDuff.Mode.SRC_IN));
        icon.setScaleType(ImageView.ScaleType.CENTER);
        circle.addView(icon, LayoutHelper.createFrame(36, 36, Gravity.CENTER));
        ll.addView(circle, LayoutHelper.createLinear(72, 72, Gravity.CENTER_HORIZONTAL, 0, 4, 0, 0));

        TextView title = new TextView(ctx);
        title.setText(LocaleController.getString(R.string.DevGramLockedChats));
        title.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText, resourceProvider));
        title.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 20);
        title.setTypeface(AndroidUtilities.bold());
        title.setGravity(Gravity.CENTER);
        ll.addView(title, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL, 0, 12, 0, 0));

        TextView subtitle = new TextView(ctx);
        subtitle.setText(LocaleController.getString(R.string.DevGramLockedChatsInfo));
        subtitle.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText, resourceProvider));
        subtitle.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        subtitle.setGravity(Gravity.CENTER);
        subtitle.setLineSpacing(AndroidUtilities.dp(2), 1f);
        ll.addView(subtitle, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL, 12, 6, 12, 8));

        return ll;
    }

    private String dialogTitle(long did) {
        try {
            if (DialogObject.isEncryptedDialog(did)) {
                return "🔒 Секретный чат";
            }
            if (DialogObject.isUserDialog(did)) {
                TLRPC.User u = getMessagesController().getUser(did);
                if (u != null) {
                    if (UserObject.isUserSelf(u)) return LocaleController.getString(R.string.SavedMessages);
                    return ContactsController.formatName(u.first_name, u.last_name);
                }
            } else {
                TLRPC.Chat c = getMessagesController().getChat(-did);
                if (c != null) return c.title;
            }
        } catch (Throwable ignore) {}
        return String.valueOf(did);
    }

    private void onItemClick(UItem item, View view, int position, float x, float y) {
        int id = item.id;
        if (id == ID_SET_PASSCODE) {
            showPasscodeDialog();
        } else if (id == ID_REMOVE_PASSCODE) {
            AlertDialog.Builder b = new AlertDialog.Builder(getContext());
            b.setTitle(LocaleController.getString(R.string.DevGramLockedChatsRemovePasscode));
            b.setMessage(LocaleController.getString(R.string.DevGramLockedChatsInfo));
            b.setPositiveButton(LocaleController.getString(R.string.OK), (d, w) -> {
                DevGramLockedChats.setPasscode(null);
                listView.adapter.update(true);
            });
            b.setNegativeButton(LocaleController.getString(R.string.Cancel), null);
            showDialog(b.create());
        } else if (id == ID_BIOMETRIC) {
            DevGramLockedChats.setBiometricEnabled(!DevGramLockedChats.biometricEnabled());
            listView.adapter.update(true);
        } else if (id == ID_HIDE_NOTIFY) {
            DevGramLockedChats.setHideNotifications(!DevGramLockedChats.hideNotifications());
            listView.adapter.update(true);
        } else if (id == ID_REVEAL) {
            DevGramLockedChats.setRevealed(!DevGramLockedChats.isRevealed());
            listView.adapter.update(true);
            getNotificationCenter().postNotificationName(NotificationCenter.dialogsNeedReload);
        } else if (id >= ID_CHAT_BASE && id - ID_CHAT_BASE < chatIds.size()) {
            long did = chatIds.get(id - ID_CHAT_BASE);
            showChatActions(did);
        }
    }

    private void showChatActions(long did) {
        AlertDialog.Builder b = new AlertDialog.Builder(getContext());
        b.setTitle(dialogTitle(did));
        b.setItems(new CharSequence[]{
                LocaleController.getString(R.string.Open),
                LocaleController.getString(R.string.DevGramLockedChatsShow)
        }, (dialog, which) -> {
            if (which == 0) {
                openChat(did);
            } else {
                DevGramLockedChats.setLocked(currentAccount, did, false);
                listView.adapter.update(true);
                getNotificationCenter().postNotificationName(NotificationCenter.dialogsNeedReload);
                BulletinFactory.of(this).createSimpleBulletin(R.raw.ic_unmute,
                        LocaleController.getString(R.string.DevGramLockedChatsShown)).show();
            }
        });
        b.setNegativeButton(LocaleController.getString(R.string.Cancel), null);
        showDialog(b.create());
    }

    private void openChat(long did) {
        android.os.Bundle args = new android.os.Bundle();
        if (DialogObject.isUserDialog(did)) {
            args.putLong("user_id", did);
        } else if (!DialogObject.isEncryptedDialog(did)) {
            args.putLong("chat_id", -did);
        } else {
            return;
        }
        if (getMessagesController().checkCanOpenChat(args, this)) {
            presentFragment(new ChatActivity(args));
        }
    }

    private void showPasscodeDialog() {
        Context ctx = getContext();
        LinearLayout ll = new LinearLayout(ctx);
        ll.setOrientation(LinearLayout.VERTICAL);

        EditTextBoldCursor first = makePinInput(ctx, LocaleController.getString(R.string.DevGramLockedChatsNewPasscode));
        EditTextBoldCursor second = makePinInput(ctx, LocaleController.getString(R.string.DevGramLockedChatsRepeatPasscode));
        ll.addView(first, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 24, 8, 24, 4));
        ll.addView(second, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 24, 4, 24, 12));

        AlertDialog.Builder b = new AlertDialog.Builder(ctx);
        b.setTitle(LocaleController.getString(hasTitleChange()));
        b.setView(ll);
        b.setPositiveButton(LocaleController.getString(R.string.Save), (dialog, which) -> {
            String p1 = first.getText() == null ? "" : first.getText().toString();
            String p2 = second.getText() == null ? "" : second.getText().toString();
            if (p1.length() < 4) {
                BulletinFactory.of(this).createErrorBulletin(LocaleController.getString(R.string.DevGramLockedChatsPasscodeShort)).show();
                return;
            }
            if (!p1.equals(p2)) {
                BulletinFactory.of(this).createErrorBulletin(LocaleController.getString(R.string.DevGramLockedChatsPasscodeMismatch)).show();
                return;
            }
            DevGramLockedChats.setPasscode(p1);
            listView.adapter.update(true);
            BulletinFactory.of(this).createSimpleBulletin(R.raw.contact_check,
                    LocaleController.getString(R.string.DevGramLockedChatsPasscodeSaved)).show();
        });
        b.setNegativeButton(LocaleController.getString(R.string.Cancel), null);
        AlertDialog dlg = b.create();
        dlg.show();
        dlg.setOnShowListener(d -> AndroidUtilities.runOnUIThread(() -> {
            first.requestFocus();
            AndroidUtilities.showKeyboard(first);
        }, 80));
    }

    private int hasTitleChange() {
        return DevGramLockedChats.hasPasscode()
                ? R.string.DevGramLockedChatsChangePasscode
                : R.string.DevGramLockedChatsSetPasscode;
    }

    private EditTextBoldCursor makePinInput(Context ctx, String hint) {
        EditTextBoldCursor e = new EditTextBoldCursor(ctx);
        e.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_VARIATION_PASSWORD);
        e.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 18);
        e.setTextColor(Theme.getColor(Theme.key_dialogTextBlack));
        e.setCursorColor(Theme.getColor(Theme.key_dialogTextBlack));
        e.setHintText(hint);
        e.setHintColor(Theme.getColor(Theme.key_dialogTextHint));
        e.setBackgroundDrawable(Theme.createEditTextDrawable(ctx, true));
        return e;
    }
}

/*
 * DevGram: экран выдачи значка конкретному ID (отдельный фрагмент, НЕ диалог).
 * Диалог-фрагмент держит только одно окно, поэтому меню выбора эмодзи/подписи закрывало бы
 * форму. Здесь форма — полноценный экран: меню и пикер эмодзи открываются поверх и не рушат её.
 * Флоу: значок (готовый ИЛИ любой эмодзи/премиум через пикер как в чате) + подпись (готовая ИЛИ
 * своя, {name} = имя профиля) -> «Выдать».
 */

package org.telegram.ui;

import android.content.Context;
import android.text.InputType;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ChatObject;
import org.telegram.messenger.DevGramBadges;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.messenger.UserObject;
import org.telegram.tgnet.TLObject;
import org.telegram.tgnet.TLRPC;
import org.telegram.tgnet.tl.TL_stars;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.AnimatedEmojiDrawable;
import org.telegram.ui.Components.AvatarDrawable;
import org.telegram.ui.Components.BackupImageView;
import org.telegram.ui.Components.EditTextBoldCursor;
import org.telegram.ui.Components.LayoutHelper;

public class DevGramBadgeGrantActivity extends BaseFragment {

    private final long dialogId;
    private long selEmojiId = DevGramBadges.EMOJI_TEAM;
    private String selText = DevGramBadges.READY_TEXTS[0];

    private TextView emojiRow;
    private TextView textRow;
    private BackupImageView badgePreview;
    private TextView previewTitle;
    private TextView previewText;
    private final TextView[] emojiOptions = new TextView[DevGramBadges.READY_EMOJI.length + 1];
    private final TextView[] textOptions = new TextView[DevGramBadges.READY_TEXTS.length + 1];
    private SelectAnimatedEmojiDialog.SelectAnimatedEmojiDialogWindow pickerPopup;

    public DevGramBadgeGrantActivity(long dialogId) {
        this.dialogId = dialogId;
        DevGramBadges.Badge current = DevGramBadges.badgeOf(dialogId);
        if (current != null) {
            selEmojiId = current.emojiId;
            selText = current.text;
        }
    }

    private static long shownId(long id) {
        return id < 0 ? -id : id;
    }

    private static String shortText(String t) {
        if (t == null) return "";
        t = t.replace("{name}", "").trim();
        return t.length() > 40 ? t.substring(0, 40) + "…" : t;
    }

    private static String emojiLabel(long emojiId) {
        for (int i = 0; i < DevGramBadges.READY_EMOJI.length; i++) {
            if (DevGramBadges.READY_EMOJI[i] == emojiId) {
                return DevGramBadges.READY_EMOJI_LABELS[i];
            }
        }
        return "свой эмодзи";
    }

    @Override
    public View createView(Context context) {
        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setAllowOverlayTitle(true);
        actionBar.setTitle(DevGramBadges.badgeOf(dialogId) == null ? "Новый значок" : "Изменить значок");
        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(int id) {
                if (id == -1) {
                    finishFragment();
                }
            }
        });

        FrameLayout root = new FrameLayout(context);
        root.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundGray, resourceProvider));

        ScrollView scroll = new ScrollView(context);
        scroll.setClipToPadding(false);
        scroll.setPadding(0, 0, 0, AndroidUtilities.dp(88));

        LinearLayout box = new LinearLayout(context);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(AndroidUtilities.dp(12), AndroidUtilities.dp(12), AndroidUtilities.dp(12), AndroidUtilities.dp(20));

        box.addView(createRecipientCard(context), LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        box.addView(header(context, "Значок"), LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 4, 18, 4, 8));
        box.addView(createEmojiChoices(context), LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        box.addView(header(context, "Подпись"), LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 4, 18, 4, 8));
        box.addView(createTextChoices(context), LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        box.addView(header(context, "Предпросмотр"), LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 4, 18, 4, 8));
        box.addView(createPreviewCard(context), LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        scroll.addView(box, new FrameLayout.LayoutParams(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
        root.addView(scroll, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));

        LinearLayout footer = new LinearLayout(context);
        footer.setPadding(AndroidUtilities.dp(12), AndroidUtilities.dp(10), AndroidUtilities.dp(12), AndroidUtilities.dp(12));
        footer.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite, resourceProvider));

        TextView grantBtn = new TextView(context);
        grantBtn.setText(DevGramBadges.badgeOf(dialogId) == null ? "Выдать значок" : "Сохранить изменения");
        grantBtn.setGravity(Gravity.CENTER);
        grantBtn.setTypeface(AndroidUtilities.bold());
        grantBtn.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
        grantBtn.setTextColor(Theme.getColor(Theme.key_featuredStickers_buttonText, resourceProvider));
        grantBtn.setBackground(Theme.createSimpleSelectorRoundRectDrawable(AndroidUtilities.dp(12),
                Theme.getColor(Theme.key_featuredStickers_addButton, resourceProvider),
                Theme.getColor(Theme.key_featuredStickers_addButtonPressed, resourceProvider)));
        grantBtn.setOnClickListener(v -> onGrant());
        footer.addView(grantBtn, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 50));
        root.addView(footer, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.BOTTOM));

        refresh();

        return fragmentView = root;
    }

    private TextView header(Context context, String text) {
        TextView view = new TextView(context);
        view.setText(text);
        view.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        view.setTypeface(AndroidUtilities.bold());
        view.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlueHeader, resourceProvider));
        return view;
    }

    private TextView choice(Context ctx, String text) {
        TextView tv = new TextView(ctx);
        tv.setText(text);
        tv.setGravity(Gravity.CENTER);
        tv.setSingleLine(true);
        tv.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        tv.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText, resourceProvider));
        tv.setPadding(AndroidUtilities.dp(14), 0, AndroidUtilities.dp(14), 0);
        tv.setBackground(Theme.createSimpleSelectorRoundRectDrawable(AndroidUtilities.dp(18),
                Theme.getColor(Theme.key_windowBackgroundWhite, resourceProvider),
                Theme.getColor(Theme.key_listSelector, resourceProvider)));
        tv.setClickable(true);
        return tv;
    }

    private View createRecipientCard(Context context) {
        LinearLayout card = new LinearLayout(context);
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setPadding(AndroidUtilities.dp(14), AndroidUtilities.dp(14), AndroidUtilities.dp(14), AndroidUtilities.dp(14));
        card.setBackground(Theme.createRoundRectDrawable(AndroidUtilities.dp(16),
                Theme.getColor(Theme.key_windowBackgroundWhite, resourceProvider)));

        TLObject peer = dialogId > 0 ? getMessagesController().getUser(dialogId) : getMessagesController().getChat(-dialogId);
        String name;
        String username = null;
        if (peer instanceof TLRPC.User) {
            name = UserObject.getUserName((TLRPC.User) peer);
            username = UserObject.getPublicUsername((TLRPC.User) peer);
        } else if (peer instanceof TLRPC.Chat) {
            name = ((TLRPC.Chat) peer).title;
            username = ChatObject.getPublicUsername((TLRPC.Chat) peer);
        } else {
            name = dialogId > 0 ? "Пользователь" : "Чат или канал";
        }

        BackupImageView avatar = new BackupImageView(context);
        avatar.setRoundRadius(AndroidUtilities.dp(28));
        AvatarDrawable avatarDrawable = new AvatarDrawable();
        if (peer != null) {
            avatarDrawable.setInfo(peer);
            avatar.setForUserOrChat(peer, avatarDrawable);
        } else {
            avatarDrawable.setInfo(dialogId, name, null);
            avatar.setImageDrawable(avatarDrawable);
        }
        card.addView(avatar, LayoutHelper.createLinear(56, 56));

        LinearLayout texts = new LinearLayout(context);
        texts.setOrientation(LinearLayout.VERTICAL);
        TextView title = new TextView(context);
        title.setText(name);
        title.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 17);
        title.setTypeface(AndroidUtilities.bold());
        title.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText, resourceProvider));
        texts.addView(title, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
        TextView sub = new TextView(context);
        sub.setText((username == null || username.isEmpty() ? "" : "@" + username + "  ·  ")
                + (dialogId > 0 ? "Пользователь" : "Чат") + "  ·  ID " + shownId(dialogId));
        sub.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
        sub.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText, resourceProvider));
        texts.addView(sub, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 3, 0, 0));
        card.addView(texts, LayoutHelper.createLinear(0, LayoutHelper.WRAP_CONTENT, 1f, 12, 0, 0, 0));
        return card;
    }

    private View createEmojiChoices(Context context) {
        HorizontalScrollView scroll = new HorizontalScrollView(context);
        scroll.setHorizontalScrollBarEnabled(false);
        LinearLayout row = new LinearLayout(context);
        row.setPadding(0, 0, AndroidUtilities.dp(4), 0);
        for (int i = 0; i < DevGramBadges.READY_EMOJI.length; i++) {
            final int index = i;
            emojiOptions[i] = choice(context, DevGramBadges.READY_EMOJI_LABELS[i]);
            emojiOptions[i].setOnClickListener(v -> {
                selEmojiId = DevGramBadges.READY_EMOJI[index];
                refresh();
            });
            row.addView(emojiOptions[i], LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, 40, i == 0 ? 0 : 8, 0, 0, 0));
        }
        emojiRow = choice(context, "＋ Свой эмодзи");
        emojiRow.setOnClickListener(v -> openEmojiPicker());
        emojiOptions[DevGramBadges.READY_EMOJI.length] = emojiRow;
        row.addView(emojiRow, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, 40, 8, 0, 0, 0));
        scroll.addView(row);
        return scroll;
    }

    private View createTextChoices(Context context) {
        HorizontalScrollView scroll = new HorizontalScrollView(context);
        scroll.setHorizontalScrollBarEnabled(false);
        LinearLayout row = new LinearLayout(context);
        row.setPadding(0, 0, AndroidUtilities.dp(4), 0);
        for (int i = 0; i < DevGramBadges.READY_TEXTS.length; i++) {
            final int index = i;
            textOptions[i] = choice(context, DevGramBadges.READY_TEXT_LABELS[i]);
            textOptions[i].setOnClickListener(v -> {
                selText = DevGramBadges.READY_TEXTS[index];
                refresh();
            });
            row.addView(textOptions[i], LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, 40, i == 0 ? 0 : 8, 0, 0, 0));
        }
        textRow = choice(context, "＋ Своя подпись");
        textRow.setOnClickListener(v -> askCustomText());
        textOptions[DevGramBadges.READY_TEXTS.length] = textRow;
        row.addView(textRow, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, 40, 8, 0, 0, 0));
        scroll.addView(row);
        return scroll;
    }

    private View createPreviewCard(Context context) {
        LinearLayout card = new LinearLayout(context);
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setPadding(AndroidUtilities.dp(16), AndroidUtilities.dp(14), AndroidUtilities.dp(16), AndroidUtilities.dp(14));
        card.setBackground(Theme.createRoundRectDrawable(AndroidUtilities.dp(16),
                Theme.getColor(Theme.key_windowBackgroundWhite, resourceProvider)));
        badgePreview = new BackupImageView(context);
        badgePreview.setRoundRadius(AndroidUtilities.dp(22));
        badgePreview.setBackground(Theme.createCircleDrawable(AndroidUtilities.dp(44),
                Theme.multAlpha(Theme.getColor(Theme.key_windowBackgroundWhiteBlueText, resourceProvider), 0.12f)));
        card.addView(badgePreview, LayoutHelper.createLinear(44, 44));
        LinearLayout texts = new LinearLayout(context);
        texts.setOrientation(LinearLayout.VERTICAL);
        previewTitle = new TextView(context);
        previewTitle.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
        previewTitle.setTypeface(AndroidUtilities.bold());
        previewTitle.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText, resourceProvider));
        texts.addView(previewTitle, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
        previewText = new TextView(context);
        previewText.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
        previewText.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText, resourceProvider));
        texts.addView(previewText, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 3, 0, 0));
        card.addView(texts, LayoutHelper.createLinear(0, LayoutHelper.WRAP_CONTENT, 1f, 12, 0, 0, 0));
        return card;
    }

    private void refresh() {
        int selectedBg = Theme.multAlpha(Theme.getColor(Theme.key_windowBackgroundWhiteBlueText, resourceProvider), 0.14f);
        int normalBg = Theme.getColor(Theme.key_windowBackgroundWhite, resourceProvider);
        int selectedText = Theme.getColor(Theme.key_windowBackgroundWhiteBlueText, resourceProvider);
        int normalText = Theme.getColor(Theme.key_windowBackgroundWhiteBlackText, resourceProvider);
        boolean readyEmoji = false;
        for (int i = 0; i < DevGramBadges.READY_EMOJI.length; i++) {
            boolean selected = selEmojiId == DevGramBadges.READY_EMOJI[i];
            readyEmoji |= selected;
            updateChoice(emojiOptions[i], selected, selectedBg, normalBg, selectedText, normalText);
        }
        updateChoice(emojiOptions[DevGramBadges.READY_EMOJI.length], !readyEmoji,
                selectedBg, normalBg, selectedText, normalText);
        boolean readyText = false;
        for (int i = 0; i < DevGramBadges.READY_TEXTS.length; i++) {
            boolean selected = DevGramBadges.READY_TEXTS[i].equals(selText);
            readyText |= selected;
            updateChoice(textOptions[i], selected, selectedBg, normalBg, selectedText, normalText);
        }
        updateChoice(textOptions[DevGramBadges.READY_TEXTS.length], !readyText,
                selectedBg, normalBg, selectedText, normalText);
        if (badgePreview != null) {
            badgePreview.setAnimatedEmojiDrawable(AnimatedEmojiDrawable.make(
                    currentAccount, AnimatedEmojiDrawable.CACHE_TYPE_ALERT_PREVIEW, selEmojiId));
        }
        String name = peerDisplayName();
        if (previewTitle != null) previewTitle.setText(name + "  ·  " + emojiLabel(selEmojiId));
        if (previewText != null) previewText.setText(selText.replace("{name}", name));
    }

    private void updateChoice(TextView view, boolean selected, int selectedBg, int normalBg,
                              int selectedText, int normalText) {
        if (view == null) return;
        view.setTypeface(selected ? AndroidUtilities.bold() : null);
        view.setTextColor(selected ? selectedText : normalText);
        view.setBackground(Theme.createSimpleSelectorRoundRectDrawable(AndroidUtilities.dp(18),
                selected ? selectedBg : normalBg, Theme.getColor(Theme.key_listSelector, resourceProvider)));
    }

    private String peerDisplayName() {
        if (dialogId > 0) {
            TLRPC.User user = getMessagesController().getUser(dialogId);
            if (user != null) return UserObject.getUserName(user);
            return "Пользователь " + shownId(dialogId);
        }
        TLRPC.Chat chat = getMessagesController().getChat(-dialogId);
        if (chat != null && chat.title != null) return chat.title;
        return "Чат " + shownId(dialogId);
    }

    // Выбор значка: готовые ИЛИ пикер эмодзи (как в чате)
    private void chooseEmoji() {
        String[] labels = DevGramBadges.READY_EMOJI_LABELS;
        String[] items = new String[labels.length + 1];
        System.arraycopy(labels, 0, items, 0, labels.length);
        items[labels.length] = "Выбрать эмодзи…";
        AlertDialog.Builder b = new AlertDialog.Builder(getParentActivity());
        b.setTitle("Значок");
        b.setItems(items, (d, which) -> {
            if (which < DevGramBadges.READY_EMOJI.length) {
                selEmojiId = DevGramBadges.READY_EMOJI[which];
                refresh();
            } else {
                openEmojiPicker();
            }
        });
        b.setNegativeButton(LocaleController.getString(R.string.Cancel), null);
        showDialog(b.create());
    }

    // Тот же пикер кастом-эмодзи, что статусы в чате: даёт document_id.
    private void openEmojiPicker() {
        if (pickerPopup != null || emojiRow == null) {
            return;
        }
        SelectAnimatedEmojiDialog layout = new SelectAnimatedEmojiDialog(
                this, getContext(), false, null, SelectAnimatedEmojiDialog.TYPE_EMOJI_STATUS, resourceProvider) {
            @Override
            protected void onEmojiSelected(View view, Long documentId, TLRPC.Document document, TL_stars.TL_starGiftUnique gift, Integer until) {
                if (documentId != null) {
                    selEmojiId = documentId;
                    refresh();
                }
                if (pickerPopup != null) {
                    pickerPopup.dismiss();
                    pickerPopup = null;
                }
            }
        };
        layout.setSelected(selEmojiId == 0 ? null : selEmojiId);
        pickerPopup = new SelectAnimatedEmojiDialog.SelectAnimatedEmojiDialogWindow(layout, LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT) {
            @Override
            public void dismiss() {
                super.dismiss();
                pickerPopup = null;
            }
        };
        pickerPopup.showAsDropDown(emojiRow, AndroidUtilities.dp(16), AndroidUtilities.dp(4), Gravity.TOP | Gravity.LEFT);
        pickerPopup.dimBehind();
    }

    // Выбор подписи: готовые ИЛИ своя
    private void chooseText() {
        String[] labels = DevGramBadges.READY_TEXT_LABELS;
        String[] items = new String[labels.length + 1];
        System.arraycopy(labels, 0, items, 0, labels.length);
        items[labels.length] = "Своя подпись…";
        AlertDialog.Builder b = new AlertDialog.Builder(getParentActivity());
        b.setTitle("Подпись");
        b.setItems(items, (d, which) -> {
            if (which < DevGramBadges.READY_TEXTS.length) {
                selText = DevGramBadges.READY_TEXTS[which];
                refresh();
            } else {
                askCustomText();
            }
        });
        b.setNegativeButton(LocaleController.getString(R.string.Cancel), null);
        showDialog(b.create());
    }

    private void askCustomText() {
        Context context = getParentActivity();
        EditTextBoldCursor et = makeInput(context, "Текст подписи ({name} = имя)",
                InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        et.setText(selText);

        FrameLayout container = new FrameLayout(context);
        container.setPadding(AndroidUtilities.dp(24), AndroidUtilities.dp(4), AndroidUtilities.dp(24), 0);
        container.addView(et, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        AlertDialog.Builder b = new AlertDialog.Builder(context);
        b.setTitle("Своя подпись");
        b.setView(container);
        b.setPositiveButton(LocaleController.getString(R.string.Done), (d, w) -> {
            String t = et.getText().toString().trim();
            if (!t.isEmpty()) {
                selText = t;
                refresh();
            }
        });
        b.setNegativeButton(LocaleController.getString(R.string.Cancel), null);
        showDialog(b.create());
        et.requestFocus();
        AndroidUtilities.showKeyboard(et);
    }

    private void onGrant() {
        ensureAdminSignedIn(() -> {
            DevGramBadges.grantBadge(dialogId, selEmojiId, selText);
            NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.reloadInterface);
            finishFragment();
        });
    }

    // --- вход команды (нужен для записи в облако) ---
    private void ensureAdminSignedIn(Runnable onReady) {
        if (DevGramBadges.isSignedIn()) {
            onReady.run();
            return;
        }
        Context context = getParentActivity();
        EditTextBoldCursor emailEt = makeInput(context, "Email команды", InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS | InputType.TYPE_CLASS_TEXT);
        EditTextBoldCursor passEt = makeInput(context, "Пароль", InputType.TYPE_TEXT_VARIATION_PASSWORD | InputType.TYPE_CLASS_TEXT);
        passEt.setTransformationMethod(android.text.method.PasswordTransformationMethod.getInstance());

        LinearLayout boxx = new LinearLayout(context);
        boxx.setOrientation(LinearLayout.VERTICAL);
        boxx.setPadding(AndroidUtilities.dp(24), AndroidUtilities.dp(4), AndroidUtilities.dp(24), 0);
        boxx.addView(emailEt, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 44));
        boxx.addView(passEt, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 44, 0f, 12f, 0f, 0f));

        AlertDialog.Builder b = new AlertDialog.Builder(context);
        b.setTitle("Вход команды DevGram");
        b.setView(boxx);
        b.setPositiveButton("Войти", (d, w) -> {
            String email = emailEt.getText().toString().trim();
            String pass = passEt.getText().toString();
            if (email.isEmpty() || pass.isEmpty()) {
                return;
            }
            DevGramBadges.signIn(email, pass, (ok, err) -> {
                if (ok) {
                    onReady.run();
                } else {
                    Toast.makeText(getParentActivity(), "Не удалось войти: " + err, Toast.LENGTH_LONG).show();
                }
            });
        });
        b.setNegativeButton(LocaleController.getString(R.string.Cancel), null);
        showDialog(b.create());
        emailEt.requestFocus();
        AndroidUtilities.showKeyboard(emailEt);
    }

    private EditTextBoldCursor makeInput(Context context, String hint, int inputType) {
        EditTextBoldCursor et = new EditTextBoldCursor(context);
        et.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 18);
        et.setTextColor(Theme.getColor(Theme.key_dialogTextBlack, resourceProvider));
        et.setInputType(inputType);
        et.setHint(hint);
        et.setHintTextColor(Theme.getColor(Theme.key_groupcreate_hintText, resourceProvider));
        et.setCursorColor(Theme.getColor(Theme.key_dialogTextBlack, resourceProvider));
        et.setCursorSize(AndroidUtilities.dp(20));
        et.setCursorWidth(1.5f);
        et.setBackgroundDrawable(Theme.createEditTextDrawable(context, true));
        return et;
    }
}

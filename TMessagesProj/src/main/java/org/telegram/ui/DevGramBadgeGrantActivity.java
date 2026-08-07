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
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.DevGramBadges;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.tgnet.TLRPC;
import org.telegram.tgnet.tl.TL_stars;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.EditTextBoldCursor;
import org.telegram.ui.Components.LayoutHelper;

public class DevGramBadgeGrantActivity extends BaseFragment {

    private final long dialogId;
    private long selEmojiId = DevGramBadges.EMOJI_TEAM;
    private String selText = DevGramBadges.READY_TEXTS[0];

    private TextView emojiRow;
    private TextView textRow;
    private SelectAnimatedEmojiDialog.SelectAnimatedEmojiDialogWindow pickerPopup;

    public DevGramBadgeGrantActivity(long dialogId) {
        this.dialogId = dialogId;
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
        actionBar.setTitle("Значок · ID " + shownId(dialogId));
        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(int id) {
                if (id == -1) {
                    finishFragment();
                }
            }
        });

        ScrollView scroll = new ScrollView(context);
        scroll.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundGray, resourceProvider));

        LinearLayout box = new LinearLayout(context);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(AndroidUtilities.dp(16), AndroidUtilities.dp(16), AndroidUtilities.dp(16), AndroidUtilities.dp(16));

        emojiRow = row(context);
        textRow = row(context);
        box.addView(emojiRow, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
        box.addView(textRow, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0f, 10f, 0f, 0f));

        TextView grantBtn = new TextView(context);
        grantBtn.setText("Выдать значок");
        grantBtn.setGravity(Gravity.CENTER);
        grantBtn.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
        grantBtn.setTextColor(Theme.getColor(Theme.key_featuredStickers_buttonText, resourceProvider));
        grantBtn.setBackground(Theme.createSimpleSelectorRoundRectDrawable(AndroidUtilities.dp(8),
                Theme.getColor(Theme.key_featuredStickers_addButton, resourceProvider),
                Theme.getColor(Theme.key_featuredStickers_addButtonPressed, resourceProvider)));
        grantBtn.setPadding(0, AndroidUtilities.dp(14), 0, AndroidUtilities.dp(14));
        grantBtn.setOnClickListener(v -> onGrant());
        box.addView(grantBtn, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0f, 22f, 0f, 0f));

        scroll.addView(box, new FrameLayout.LayoutParams(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        emojiRow.setOnClickListener(v -> chooseEmoji());
        textRow.setOnClickListener(v -> chooseText());
        refresh();

        return fragmentView = scroll;
    }

    private TextView row(Context ctx) {
        TextView tv = new TextView(ctx);
        tv.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
        tv.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText, resourceProvider));
        tv.setPadding(AndroidUtilities.dp(16), AndroidUtilities.dp(16), AndroidUtilities.dp(16), AndroidUtilities.dp(16));
        tv.setBackground(Theme.createSimpleSelectorRoundRectDrawable(AndroidUtilities.dp(10),
                Theme.getColor(Theme.key_windowBackgroundWhite, resourceProvider),
                Theme.getColor(Theme.key_listSelector, resourceProvider)));
        tv.setClickable(true);
        return tv;
    }

    private void refresh() {
        emojiRow.setText("Значок:  " + emojiLabel(selEmojiId));
        textRow.setText("Подпись:  " + shortText(selText));
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

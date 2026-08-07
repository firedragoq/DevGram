/*
 * DevGram: экран выдачи значков (доступен команде проекта).
 * Флоу: ввод ID -> выбор значка (готовый ИЛИ любой эмодзи/премиум-эмодзи через тот же пикер,
 * что в чате) -> выбор подписи (готовая ИЛИ своя) -> выдать. Значок = {эмодзи, подпись} и
 * хранится в облаке (Firebase RTDB), появляется у всех сразу, пересборка не нужна.
 */

package org.telegram.ui;

import android.content.Context;
import android.text.InputType;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.Toast;

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
    private ArrayList<Long> granted = new ArrayList<>(); // dialogId'ы
    private String query = ""; // фильтр поиска (по ID и юзернейму/имени)

    @Override
    public View createView(Context context) {
        DevGramBadges.syncFromCloud(); // подтянуть свежий список из облака при открытии
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

        LinearLayout root = new LinearLayout(context);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundGray, resourceProvider));

        // Поиск по выданным значкам (по ID и юзернейму/имени)
        android.widget.FrameLayout searchWrap = new android.widget.FrameLayout(context);
        searchWrap.setBackground(Theme.createRoundRectDrawable(AndroidUtilities.dp(10),
                Theme.getColor(Theme.key_windowBackgroundWhite, resourceProvider)));
        android.widget.ImageView searchIcon = new android.widget.ImageView(context);
        searchIcon.setImageResource(R.drawable.msg_search);
        searchIcon.setColorFilter(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText, resourceProvider));
        searchWrap.addView(searchIcon, LayoutHelper.createFrame(20, 20, Gravity.CENTER_VERTICAL | Gravity.LEFT, 14, 0, 0, 0));
        android.widget.EditText search = new android.widget.EditText(context);
        search.setHint("Поиск по ID или @юзернейму");
        search.setSingleLine(true);
        search.setBackground(null);
        search.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
        search.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText, resourceProvider));
        search.setHintTextColor(Theme.getColor(Theme.key_groupcreate_hintText, resourceProvider));
        search.setPadding(AndroidUtilities.dp(44), 0, AndroidUtilities.dp(14), 0);
        search.addTextChangedListener(new android.text.TextWatcher() {
            public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
            public void onTextChanged(CharSequence s, int a, int b, int c) {}
            public void afterTextChanged(android.text.Editable s) {
                query = s.toString().trim().toLowerCase();
                update();
            }
        });
        searchWrap.addView(search, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 42));
        root.addView(searchWrap, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 42, 10, 10, 10, 6));

        listView = new UniversalRecyclerView(this, this::fillItems, this::onItemClick, null);
        listView.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundGray, resourceProvider));
        root.addView(listView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 0, 1f));
        actionBar.setAdaptiveBackground(listView);

        return fragmentView = root;
    }

    // Имя + @юзернейм по dialogId (для отображения и поиска). "" если не резолвится.
    private String nameFor(long dialogId) {
        try {
            if (dialogId > 0) {
                org.telegram.tgnet.TLRPC.User u = getMessagesController().getUser(dialogId);
                if (u != null) {
                    String nm = org.telegram.messenger.ContactsController.formatName(u.first_name, u.last_name);
                    String un = org.telegram.messenger.UserObject.getPublicUsername(u);
                    return (nm != null ? nm : "") + (un != null && !un.isEmpty() ? " @" + un : "");
                }
            } else {
                org.telegram.tgnet.TLRPC.Chat c = getMessagesController().getChat(-dialogId);
                if (c != null) {
                    String un = org.telegram.messenger.ChatObject.getPublicUsername(c);
                    return (c.title != null ? c.title : "") + (un != null && !un.isEmpty() ? " @" + un : "");
                }
            }
        } catch (Throwable ignore) {
        }
        return "";
    }

    private static long shownId(long dialogId) {
        return dialogId < 0 ? -dialogId : dialogId;
    }

    private static String shortText(String t) {
        if (t == null) return "";
        t = t.replace("{name}", "").trim();
        return t.length() > 34 ? t.substring(0, 34) + "…" : t;
    }

    private void fillItems(ArrayList<UItem> items, UniversalAdapter adapter) {
        items.add(UItem.asButton(ID_ADD, R.drawable.msg_contact_add, "Выдать значок по ID"));
        items.add(UItem.asShadow("Введите ID, выберите значок (готовый или любой эмодзи/премиум-эмодзи) "
                + "и подпись (готовую или свою). Значок появится сразу у всех, без пересборки."));

        granted = DevGramBadges.listGranted();
        if (!granted.isEmpty()) {
            items.add(UItem.asHeader("Выдано вручную (" + granted.size() + ")"));
            int shown = 0;
            for (int i = 0; i < granted.size(); i++) {
                long dialogId = granted.get(i);
                String name = nameFor(dialogId);
                // фильтр: по ID и по имени/юзернейму
                if (!query.isEmpty()
                        && !String.valueOf(shownId(dialogId)).contains(query)
                        && !name.toLowerCase().contains(query)) {
                    continue;
                }
                DevGramBadges.Badge b = DevGramBadges.badgeOf(dialogId);
                String title = name.isEmpty() ? ("ID " + shownId(dialogId)) : name;
                String value = name.isEmpty() ? (b == null ? "" : shortText(b.text))
                        : ("ID " + shownId(dialogId));
                items.add(UItem.asButton(ID_LIST_BASE + i, title, value));
                shown++;
            }
            if (shown == 0) {
                items.add(UItem.asShadow("Ничего не найдено."));
            } else {
                items.add(UItem.asShadow("Нажмите на строку, чтобы снять значок."));
            }
        }
    }

    private void onItemClick(UItem item, View view, int position, float x, float y) {
        if (item.id == ID_ADD) {
            askId();
        } else if (item.id >= ID_LIST_BASE) {
            int idx = item.id - ID_LIST_BASE;
            if (idx >= 0 && idx < granted.size()) {
                showRevokeDialog(granted.get(idx));
            }
        }
    }

    // Ввод ID -> открываем ОТДЕЛЬНЫЙ экран выдачи (не диалог, иначе пикер эмодзи его закроет).
    private void askId() {
        Context context = getParentActivity();
        EditTextBoldCursor editText = makeInput(context, "ID пользователя (или -ID чата)", InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_SIGNED);

        android.widget.FrameLayout container = new android.widget.FrameLayout(context);
        container.setPadding(AndroidUtilities.dp(24), AndroidUtilities.dp(4), AndroidUtilities.dp(24), 0);
        container.addView(editText, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 44));

        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setTitle("Кому выдать значок?");
        builder.setView(container);
        builder.setPositiveButton("Далее", (d, w) -> {
            long id = Utilities.parseLong(editText.getText().toString());
            if (id != 0) {
                presentFragment(new DevGramBadgeGrantActivity(id));
            }
        });
        builder.setNegativeButton(LocaleController.getString(R.string.Cancel), null);
        showDialog(builder.create());
        editText.requestFocus();
        AndroidUtilities.showKeyboard(editText);
    }

    // Обновляем список при возврате с экрана выдачи.
    @Override
    public void onResume() {
        super.onResume();
        DevGramBadges.syncFromCloud();
        update();
    }

    private void showRevokeDialog(long dialogId) {
        DevGramBadges.Badge b = DevGramBadges.badgeOf(dialogId);
        AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
        builder.setTitle("Снять значок");
        builder.setMessage("Убрать значок у ID " + shownId(dialogId) + "?"
                + (b != null ? "\n\n«" + shortText(b.text) + "»" : ""));
        builder.setPositiveButton("Снять", (d, w) -> ensureAdminSignedIn(() -> {
            DevGramBadges.revoke(dialogId);
            update();
        }));
        builder.setNegativeButton(LocaleController.getString(R.string.Cancel), null);
        showDialog(builder.create());
    }

    // Писать значки в облако можно только после входа команды (Firebase Auth REST).
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
        // видимый hint управляется setHintTextColor (setHintColor — анимированный, не тот)
        et.setHintTextColor(Theme.getColor(Theme.key_groupcreate_hintText, resourceProvider));
        et.setCursorColor(Theme.getColor(Theme.key_dialogTextBlack, resourceProvider));
        et.setCursorSize(AndroidUtilities.dp(20));
        et.setCursorWidth(1.5f);
        et.setBackgroundDrawable(Theme.createEditTextDrawable(context, true));
        return et;
    }

    private void update() {
        if (listView != null && listView.adapter != null) {
            listView.adapter.update(true);
        }
        org.telegram.messenger.NotificationCenter.getGlobalInstance()
                .postNotificationName(org.telegram.messenger.NotificationCenter.reloadInterface);
    }
}

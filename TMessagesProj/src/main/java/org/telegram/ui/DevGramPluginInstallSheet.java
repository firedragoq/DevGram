/*
 * DevGram: лист установки плагина. Появляется при тапе на файл .plugin в чате/канале —
 * показывает метаданные (имя, версия, автор, описание), предупреждение и кнопку установки.
 */

package org.telegram.ui;

import android.content.Context;
import android.graphics.Typeface;
import android.text.InputType;
import android.text.method.PasswordTransformationMethod;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import org.telegram.messenger.DevGramBadges;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.Components.EditTextBoldCursor;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.DevGramPlugins;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.BottomSheet;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.CheckBoxCell;
import org.telegram.ui.Components.LayoutHelper;

public class DevGramPluginInstallSheet {

    public static void show(BaseFragment fragment, String source) {
        show(fragment, source, 0);
    }

    // Показать лист установки для исходника плагина.
    // sourceDialogId — диалог, откуда открыт плагин (у канала отрицательный). Если это
    // канал-разработчик плагинов (значок 🧩), плагин автоматически считается проверенным.
    public static void show(BaseFragment fragment, String source, long sourceDialogId) {
        Context context = fragment.getParentActivity();
        if (context == null || source == null) {
            return;
        }
        String meta = DevGramPlugins.parseMeta(source);
        if (meta == null || meta.isEmpty()) {
            org.telegram.ui.Components.BulletinFactory.of(fragment)
                    .createErrorBulletin("Это не похоже на плагин DevGram").show();
            return;
        }
        // плагин из канала-разработчика (🧩) — помечаем доверенным (переживёт синхронизацию)
        if (org.telegram.messenger.DevGramBadges.isPluginDevChannel(sourceDialogId)) {
            DevGramPlugins.trustFromChannel(source);
        }
        String[] m = meta.split("", -1);
        final String id = m.length > 0 ? m[0] : "";
        String name = m.length > 1 && !m[1].isEmpty() ? m[1] : (id.isEmpty() ? "Плагин" : id);
        String ver = m.length > 2 ? m[2] : "";
        String author = m.length > 3 ? m[3] : "";
        String desc = m.length > 4 ? m[4] : "";
        String iconUrl = m.length > 5 ? m[5] : "";
        final boolean fromDevChannel = org.telegram.messenger.DevGramBadges.isPluginDevChannel(sourceDialogId);
        final boolean verified = DevGramPlugins.isVerified(source);

        LinearLayout root = new LinearLayout(context);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(AndroidUtilities.dp(20), AndroidUtilities.dp(16), AndroidUtilities.dp(20), AndroidUtilities.dp(12));

        // иконка (по умолчанию — общая; если у плагина задан icon-URL, подгрузим аву)
        android.widget.ImageView icon = new android.widget.ImageView(context);
        try {
            icon.setImageResource(R.drawable.devgram_cat_general);
        } catch (Throwable ignore) {
        }
        icon.setColorFilter(Theme.getColor(Theme.key_windowBackgroundWhiteBlueText));
        icon.setClipToOutline(true);
        icon.setOutlineProvider(new android.view.ViewOutlineProvider() {
            @Override
            public void getOutline(View v, android.graphics.Outline outline) {
                outline.setRoundRect(0, 0, v.getWidth(), v.getHeight(), AndroidUtilities.dp(16));
            }
        });
        if (!iconUrl.isEmpty()) {
            loadIcon(icon, iconUrl);
        }
        root.addView(icon, LayoutHelper.createLinear(64, 64, Gravity.CENTER_HORIZONTAL, 0, 4, 0, 0));

        // название
        TextView title = new TextView(context);
        title.setText(name);
        title.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
        title.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 20);
        title.setTypeface(AndroidUtilities.bold());
        title.setGravity(Gravity.CENTER);
        root.addView(title, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 12, 0, 0));

        // версия + автор
        StringBuilder sub = new StringBuilder();
        if (!ver.isEmpty()) {
            sub.append("Версия ").append(ver);
        }
        if (!author.isEmpty()) {
            if (sub.length() > 0) {
                sub.append("  •  ");
            }
            sub.append(author);
        }
        if (sub.length() > 0) {
            TextView subtitle = new TextView(context);
            subtitle.setText(sub.toString());
            subtitle.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText2));
            subtitle.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
            subtitle.setGravity(Gravity.CENTER);
            root.addView(subtitle, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 4, 0, 0));
        }

        // бейдж проверки (как у exteraGram, в нашем стиле)
        TextView badge = new TextView(context);
        badge.setText(verified ? (fromDevChannel ? "🧩 Проверенный разработчик" : "🛡️ Проверено DevGram") : "⚠️ Неизвестный источник");
        badge.setTextColor(verified ? 0xFFFFFFFF : Theme.getColor(Theme.key_windowBackgroundWhiteGrayText));
        badge.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
        badge.setTypeface(AndroidUtilities.bold());
        badge.setPadding(AndroidUtilities.dp(14), AndroidUtilities.dp(7), AndroidUtilities.dp(14), AndroidUtilities.dp(7));
        badge.setBackground(Theme.createRoundRectDrawable(AndroidUtilities.dp(16),
                verified ? 0xFF3BA55D : Theme.getColor(Theme.key_windowBackgroundGray)));
        root.addView(badge, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL, 0, 14, 0, 0));

        // описание
        if (!desc.isEmpty()) {
            TextView description = new TextView(context);
            description.setText(desc);
            description.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
            description.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
            description.setGravity(Gravity.CENTER);
            root.addView(description, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 16, 0, 0));
        }

        // предупреждение / подтверждение (по статусу проверки)
        LinearLayout warn = new LinearLayout(context);
        warn.setOrientation(LinearLayout.VERTICAL);
        warn.setBackground(Theme.createRoundRectDrawable(AndroidUtilities.dp(12),
                verified ? 0x223BA55D : Theme.getColor(Theme.key_windowBackgroundGray)));
        warn.setPadding(AndroidUtilities.dp(14), AndroidUtilities.dp(12), AndroidUtilities.dp(14), AndroidUtilities.dp(12));
        TextView warnText = new TextView(context);
        warnText.setText(verified
                ? (fromDevChannel
                    ? "✓ Проверенный источник\nПлагин из канала-разработчика плагинов DevGram (🧩). Такие каналы проверены командой — плагинам из них можно доверять."
                    : "✓ Проверенный плагин\nОн есть в реестре DevGram — код проверен, можно доверять.")
                : "ⓘ Внимание: неизвестный источник\nПлагина нет в реестре проверенных DevGram, он выполняет код в приложении. Устанавливай только если доверяешь автору.");
        warnText.setTextColor(verified ? 0xFF3BA55D : Theme.getColor(Theme.key_windowBackgroundWhiteGrayText));
        warnText.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
        warn.addView(warnText, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
        root.addView(warn, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 18, 0, 0));

        // галка «включить после установки»
        final boolean[] enableAfter = {true};
        CheckBoxCell check = new CheckBoxCell(context, 1, fragment.getResourceProvider());
        check.setText("Включить после установки", "", true, false);
        check.setOnClickListener(v -> {
            enableAfter[0] = !enableAfter[0];
            check.setChecked(enableAfter[0], true);
        });
        root.addView(check, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 8, 0, 0));

        // кнопка «Установить»
        TextView install = new TextView(context);
        install.setText(verified ? "Установить плагин" : "Установить неизвестный плагин");
        install.setGravity(Gravity.CENTER);
        install.setTextColor(Theme.getColor(Theme.key_featuredStickers_buttonText));
        install.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
        install.setTypeface(AndroidUtilities.bold());
        install.setBackground(Theme.createSimpleSelectorRoundRectDrawable(AndroidUtilities.dp(10),
                Theme.getColor(Theme.key_featuredStickers_addButton),
                Theme.getColor(Theme.key_featuredStickers_addButtonPressed)));
        root.addView(install, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 48, 0, 12, 0, 0));

        // ---- перекраска по статусу проверки (живьём) ----
        final boolean[] vState = {verified};
        final TextView badgeRef = badge, warnTextRef = warnText, installRef = install;
        final LinearLayout warnRef = warn;
        Runnable applyVerified = () -> {
            boolean vv = vState[0];
            badgeRef.setText(vv ? "🛡️ Проверено DevGram" : "⚠️ Неизвестный источник");
            badgeRef.setTextColor(vv ? 0xFFFFFFFF : Theme.getColor(Theme.key_windowBackgroundWhiteGrayText));
            badgeRef.setBackground(Theme.createRoundRectDrawable(AndroidUtilities.dp(16),
                    vv ? 0xFF3BA55D : Theme.getColor(Theme.key_windowBackgroundGray)));
            warnRef.setBackground(Theme.createRoundRectDrawable(AndroidUtilities.dp(12),
                    vv ? 0x223BA55D : Theme.getColor(Theme.key_windowBackgroundGray)));
            warnTextRef.setText(vv
                    ? "✓ Проверенный плагин\nОн есть в реестре DevGram — код проверен, можно доверять."
                    : "ⓘ Внимание: неизвестный источник\nПлагина нет в реестре проверенных DevGram, он выполняет код в приложении. Устанавливай только если доверяешь автору.");
            warnTextRef.setTextColor(vv ? 0xFF3BA55D : Theme.getColor(Theme.key_windowBackgroundWhiteGrayText));
            installRef.setText(vv ? "Установить плагин" : "Установить неизвестный плагин");
        };

        final boolean team = org.telegram.messenger.DevGramBadges.isTeam(fragment.getUserConfig().getClientUserId());

        // команда: сделать проверенным / убрать
        if (team) {
            TextView teamBtn = new TextView(context);
            teamBtn.setGravity(Gravity.CENTER);
            teamBtn.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
            teamBtn.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlueText));
            teamBtn.setPadding(0, AndroidUtilities.dp(12), 0, AndroidUtilities.dp(6));
            final TextView tb = teamBtn;
            Runnable applyTeam = () -> tb.setText(vState[0] ? "✕ Убрать из проверенных" : "✓ Сделать проверенным");
            applyTeam.run();
            teamBtn.setOnClickListener(v -> ensureAdmin(fragment, () -> {
                boolean ok = vState[0] ? DevGramPlugins.unverify(source) : DevGramPlugins.verify(source);
                if (ok) {
                    vState[0] = !vState[0];
                    applyVerified.run();
                    applyTeam.run();
                    org.telegram.ui.Components.BulletinFactory.of(fragment)
                            .createSimpleBulletin(R.raw.contact_check, vState[0] ? "Плагин помечен проверенным" : "Проверка снята").show();
                }
            }));
            root.addView(teamBtn, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 2, 0, 0));
        }

        // Опубликовать в каталог — команда ИЛИ АДМИН канала-разработчика (🧩).
        // Обычный подписчик дев-канала кнопку НЕ видит (только тот, кто может там постить).
        boolean channelAdmin = false;
        if (fromDevChannel && sourceDialogId < 0) {
            org.telegram.tgnet.TLRPC.Chat pubChat = fragment.getMessagesController().getChat(-sourceDialogId);
            channelAdmin = org.telegram.messenger.ChatObject.hasAdminRights(pubChat);
        }
        if (team || channelAdmin) {
            TextView pubBtn = new TextView(context);
            pubBtn.setGravity(Gravity.CENTER);
            pubBtn.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
            pubBtn.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlueText));
            pubBtn.setPadding(0, AndroidUtilities.dp(team ? 4 : 12), 0, AndroidUtilities.dp(6));
            pubBtn.setText("📚 Опубликовать в каталог");
            final String fId = id, fName = name, fVer = ver, fAuthor = author, fDesc = desc, fIcon = iconUrl, fSource = source;
            final long fChannelId = sourceDialogId;
            pubBtn.setOnClickListener(v -> {
                DevGramPlugins.CatalogEntry ce = new DevGramPlugins.CatalogEntry();
                ce.id = fId;
                ce.name = fName;
                ce.version = fVer;
                ce.author = fAuthor;
                ce.desc = fDesc;
                ce.icon = fIcon;
                ce.source = fSource;
                ce.channel = channelName(fragment, fChannelId);
                choosePublishFilter(fragment, ce);
            });
            root.addView(pubBtn, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 0, 0, 0));

            // Плагин уже на модерации / одобрен / отклонён / заблокирован — прячем кнопку (дубликат не нужен).
            // Показываем сразу (мгновенно для новой публикации), скрываем асинхронно после проверки состояния.
            final TextView pubBtnRef = pubBtn;
            DevGramPlugins.isPluginSubmitted(fId, fSource, submitted -> {
                if (submitted) {
                    pubBtnRef.setVisibility(android.view.View.GONE);
                }
            });
        }

        ScrollView scroll = new ScrollView(context);
        scroll.addView(root);

        BottomSheet.Builder builder = new BottomSheet.Builder(context);
        builder.setApplyBottomPadding(false);
        builder.setCustomView(scroll);
        BottomSheet sheet = builder.create();

        install.setOnClickListener(v -> {
            boolean ok = DevGramPlugins.install(source, id, enableAfter[0]);
            sheet.dismiss();
            if (ok) {
                org.telegram.ui.Components.BulletinFactory.of(fragment)
                        .createSimpleBulletin(R.raw.contact_check, "Плагин «" + name + "» установлен").show();
            } else {
                org.telegram.ui.Components.BulletinFactory.of(fragment)
                        .createErrorBulletin("Не удалось установить плагин").show();
            }
        });

        sheet.show();
    }

    // Публикация в каталог: сначала выбор фильтра (категории), затем запись. Заблокированные плагины
    // (удалённые командой) публиковать нельзя.
    private static void choosePublishFilter(BaseFragment fragment, DevGramPlugins.CatalogEntry ce) {
        if (DevGramPlugins.isBlocked(ce.source)) {
            org.telegram.ui.Components.BulletinFactory.of(fragment)
                    .createErrorBulletin("Этот плагин удалён командой — публиковать нельзя").show();
            return;
        }
        DevGramPlugins.fetchFilters(filters -> {
            Context ctx = fragment.getParentActivity();
            if (ctx == null) {
                return;
            }
            final java.util.ArrayList<String> opts = new java.util.ArrayList<>();
            opts.add("Без фильтра");
            opts.addAll(filters);
            AlertDialog.Builder b = new AlertDialog.Builder(ctx);
            b.setTitle("Куда опубликовать (фильтр)");
            b.setItems(opts.toArray(new CharSequence[0]), (d, which) -> {
                ce.filter = which == 0 ? "" : opts.get(which);
                int r = DevGramPlugins.publishToCatalog(ce);
                String msg = r == 1 ? "Опубликовано в каталог"
                        : (r == -1 ? "Плагин заблокирован — публикация запрещена" : "Не удалось опубликовать");
                org.telegram.ui.Components.BulletinFactory.of(fragment)
                        .createSimpleBulletin(R.raw.contact_check, msg).show();
            });
            b.setNegativeButton(LocaleController.getString(R.string.Cancel), null);
            fragment.showDialog(b.create());
        });
    }

    // Имя канала-источника (@username или заголовок) по dialogId (у канала он отрицательный).
    private static String channelName(BaseFragment fragment, long dialogId) {
        if (dialogId >= 0) {
            return "";
        }
        try {
            org.telegram.tgnet.TLRPC.Chat chat = fragment.getMessagesController().getChat(-dialogId);
            if (chat != null) {
                String u = org.telegram.messenger.ChatObject.getPublicUsername(chat);
                if (u != null && !u.isEmpty()) {
                    return "@" + u;
                }
                if (chat.title != null) {
                    return chat.title;
                }
            }
        } catch (Throwable ignore) {
        }
        return "";
    }

    // Требуем вход команды (email + пароль) прямо здесь, если ещё не вошли.
    private static void ensureAdmin(BaseFragment fragment, Runnable onReady) {
        if (DevGramPlugins.canManageVerified()) {
            onReady.run();
            return;
        }
        Context context = fragment.getParentActivity();
        if (context == null) {
            return;
        }
        EditTextBoldCursor emailEt = makeInput(context, "Email команды", InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS | InputType.TYPE_CLASS_TEXT);
        EditTextBoldCursor passEt = makeInput(context, "Пароль", InputType.TYPE_TEXT_VARIATION_PASSWORD | InputType.TYPE_CLASS_TEXT);
        passEt.setTransformationMethod(PasswordTransformationMethod.getInstance());

        LinearLayout box = new LinearLayout(context);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(AndroidUtilities.dp(24), AndroidUtilities.dp(4), AndroidUtilities.dp(24), 0);
        box.addView(emailEt, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 44));
        box.addView(passEt, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 44, 0f, 12f, 0f, 0f));

        AlertDialog.Builder b = new AlertDialog.Builder(context);
        b.setTitle("Вход команды DevGram");
        b.setView(box);
        b.setPositiveButton("Войти", (d, w) -> {
            String email = emailEt.getText().toString().trim();
            String pass = passEt.getText().toString();
            if (email.isEmpty() || pass.isEmpty()) {
                return;
            }
            DevGramBadges.signIn(email, pass, (ok, err) -> AndroidUtilities.runOnUIThread(() -> {
                if (ok) {
                    onReady.run();
                } else {
                    org.telegram.ui.Components.BulletinFactory.of(fragment).createErrorBulletin("Не удалось войти: " + err).show();
                }
            }));
        });
        b.setNegativeButton(LocaleController.getString(R.string.Cancel), null);
        fragment.showDialog(b.create());
        emailEt.requestFocus();
        AndroidUtilities.showKeyboard(emailEt);
    }

    // Аватарка плагina по URL: кеш → иначе качаем в фоне → ставим на UI-потоке.
    private static void loadIcon(final android.widget.ImageView iv, final String url) {
        final Context ctx = iv.getContext();
        if (ctx == null) return;
        java.io.File dir = new java.io.File(ctx.getCacheDir(), "devgram_icons");
        dir.mkdirs();
        final java.io.File cache = new java.io.File(dir, Integer.toHexString(url.hashCode()) + ".img");
        if (cache.exists() && cache.length() > 0) {
            android.graphics.Bitmap bmp = android.graphics.BitmapFactory.decodeFile(cache.getAbsolutePath());
            if (bmp != null) {
                applyIcon(iv, bmp);
                return;
            }
        }
        new Thread(() -> {
            try {
                java.net.HttpURLConnection c = (java.net.HttpURLConnection) new java.net.URL(url).openConnection();
                c.setConnectTimeout(10000);
                c.setReadTimeout(15000);
                c.setInstanceFollowRedirects(true);
                c.setRequestProperty("User-Agent", "DevGram");
                java.io.InputStream in = c.getInputStream();
                java.io.File tmp = new java.io.File(cache.getAbsolutePath() + ".tmp");
                java.io.FileOutputStream out = new java.io.FileOutputStream(tmp);
                byte[] buf = new byte[8192];
                int n;
                while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
                out.close();
                in.close();
                c.disconnect();
                tmp.renameTo(cache);
                final android.graphics.Bitmap bmp = android.graphics.BitmapFactory.decodeFile(cache.getAbsolutePath());
                if (bmp != null) {
                    AndroidUtilities.runOnUIThread(() -> applyIcon(iv, bmp));
                }
            } catch (Throwable e) {
                org.telegram.messenger.FileLog.e(e);
            }
        }).start();
    }

    private static void applyIcon(android.widget.ImageView iv, android.graphics.Bitmap bmp) {
        iv.setColorFilter(null);
        iv.setScaleType(android.widget.ImageView.ScaleType.CENTER_CROP);
        iv.setImageBitmap(bmp);
    }

    private static EditTextBoldCursor makeInput(Context context, String hint, int inputType) {
        EditTextBoldCursor et = new EditTextBoldCursor(context);
        et.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 18);
        et.setTextColor(Theme.getColor(Theme.key_dialogTextBlack));
        et.setInputType(inputType);
        et.setHint(hint);
        et.setHintColor(Theme.getColor(Theme.key_dialogTextHint));
        et.setCursorColor(Theme.getColor(Theme.key_dialogTextBlack));
        return et;
    }
}

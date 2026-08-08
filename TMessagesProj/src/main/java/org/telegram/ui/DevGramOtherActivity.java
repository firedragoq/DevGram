package org.telegram.ui;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.TextView;

import org.telegram.messenger.DevGramConfig;
import org.telegram.messenger.R;
import org.telegram.messenger.browser.Browser;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.BulletinFactory;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.UItem;
import org.telegram.ui.Components.UniversalAdapter;
import org.telegram.ui.Components.UniversalRecyclerView;

import java.util.ArrayList;

// DevGram: раздел «Другое» — поддержка проекта, сбор данных и сервисные действия (экспорт/импорт/сброс,
// удаление аккаунта). Открывается кнопкой «Другое» из главного экрана настроек DevGram.
public class DevGramOtherActivity extends BaseFragment {

    private static final int ID_SUPPORT_BOOSTY = 2;
    private static final int ID_SUPPORT_TON = 3;
    private static final int ID_CRASHLYTICS = 10;
    private static final int ID_ANALYTICS = 11;
    private static final int ID_EXPORT = 20;
    private static final int ID_IMPORT = 21;
    private static final int ID_RESET = 22;
    private static final int ID_DELETE_ACCOUNT = 23;
    private static final int REQ_IMPORT = 9021;

    private static final String LINK_BOOSTY = "https://boosty.to/devgram";
    private static final String TON_ADDRESS = "UQD9m9PQ5BfQa_sAA09HqJ2WLfmFDoyPxsZ8kSEyD-lXMYsx";
    private static final String LINK_TONKEEPER = "https://app.tonkeeper.com/transfer/" + TON_ADDRESS;

    private UniversalRecyclerView listView;

    @Override
    public View createView(Context context) {
        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setAllowOverlayTitle(true);
        actionBar.setTitle("Другое");
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
        Context ctx = getContext();

        // Поддержка — реквизиты; серая подпись снизу кликабельна и открывает лист «Поддержать DevGram».
        items.add(UItem.asHeader("Поддержка"));
        items.add(UItem.asButton(ID_SUPPORT_BOOSTY,
                ctx != null ? ctx.getDrawable(R.drawable.devgram_boosty) : null, "Boosty"));
        items.add(UItem.asButton(ID_SUPPORT_TON,
                ctx != null ? ctx.getDrawable(R.drawable.devgram_tonkeeper) : null, "Tonkeeper"));
        items.add(UItem.asShadow(supportCaption()));

        // Сбор данных
        items.add(UItem.asHeader("Сбор данных"));
        items.add(UItem.asCheck(ID_CRASHLYTICS, "Отчёты о сбоях").setChecked(DevGramConfig.crashlyticsEnabled));
        items.add(UItem.asCheck(ID_ANALYTICS, "Аналитика").setChecked(DevGramConfig.analyticsEnabled));
        items.add(UItem.asShadow("Анонимные данные (версия, модель устройства, факт запуска, стек-трейсы "
                + "сбоев) отправляются в базу DevGram, чтобы мы видели статистику и чинили ошибки. Без "
                + "Google-SDK и без привязки к аккаунту. Отчёты о сбоях включены по умолчанию, аналитика "
                + "— выключена. Любой тумблер можно отключить."));

        // Настройки мода
        items.add(UItem.asHeader("Настройки"));
        items.add(UItem.asButton(ID_EXPORT, R.drawable.msg_share, "Экспортировать настройки"));
        items.add(UItem.asButton(ID_IMPORT, R.drawable.msg_download, "Импортировать настройки"));
        items.add(UItem.asButton(ID_RESET, R.drawable.msg_reset, "Сбросить настройки"));
        items.add(UItem.asShadow("Экспорт отправит файл .devgram выбранному чату. Импорт покажет отличия и "
                + "предложит применить."));
        items.add(UItem.asButton(ID_DELETE_ACCOUNT, R.drawable.msg_delete, "Удалить аккаунт").red());
        items.add(UItem.asShadow(null));
    }

    // Серая подпись под реквизитами: часть «Поддержите разработку» кликабельна (цвет как у всего
    // текста подписи, без выделения), открывает лист «Поддержать DevGram».
    private CharSequence supportCaption() {
        String full = "Поддержи DevGram донатом и забери эксклюзивный значок в профиль.";
        String link = "Поддержи DevGram донатом";
        android.text.SpannableString sp = new android.text.SpannableString(full);
        sp.setSpan(new android.text.style.ClickableSpan() {
            @Override
            public void onClick(View widget) {
                DevGramSupportSheet.show(DevGramOtherActivity.this);
            }

            @Override
            public void updateDrawState(android.text.TextPaint ds) {
                super.updateDrawState(ds);
                // явно красим в серый цвет footer-текста, иначе ячейка красит ссылку в синий
                ds.setColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText4));
                ds.setUnderlineText(false);
            }
        }, 0, link.length(), android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        return sp;
    }

    private void onItemClick(UItem item, View view, int position, float x, float y) {
        if (item.id == ID_SUPPORT_BOOSTY) {
            Browser.openUrl(getContext(), LINK_BOOSTY);
        } else if (item.id == ID_SUPPORT_TON) {
            openTonkeeper();
        } else if (item.id == ID_CRASHLYTICS) {
            DevGramConfig.setCrashlyticsEnabled(!DevGramConfig.crashlyticsEnabled);
            refreshList();
        } else if (item.id == ID_ANALYTICS) {
            DevGramConfig.setAnalyticsEnabled(!DevGramConfig.analyticsEnabled);
            refreshList();
        } else if (item.id == ID_EXPORT) {
            exportSettings();
        } else if (item.id == ID_IMPORT) {
            importSettings();
        } else if (item.id == ID_RESET) {
            confirmReset();
        } else if (item.id == ID_DELETE_ACCOUNT) {
            confirmDeleteAccount();
        }
    }

    private void refreshList() {
        if (listView != null && listView.adapter != null) {
            listView.adapter.update(true);
        }
    }

    // Экспорт: обычная пересылка — выбор чата и отправка файла .devgram.
    private void exportSettings() {
        java.io.File f = DevGramSettingsIO.writeExportFile();
        if (f == null) {
            BulletinFactory.of(this).createErrorBulletin("Не удалось подготовить файл").show();
            return;
        }
        Bundle args = new Bundle();
        args.putBoolean("onlySelect", true);
        args.putInt("dialogsType", DialogsActivity.DIALOGS_TYPE_FORWARD);
        DialogsActivity picker = new DialogsActivity(args);
        picker.setDelegate((fragment1, dids, m, param, notify, scheduleDate, scheduleRepeatPeriod, topicsFragment) -> {
            if (dids.isEmpty()) {
                return false;
            }
            long did = dids.get(0).dialogId;
            org.telegram.messenger.SendMessagesHelper.prepareSendingDocument(
                    org.telegram.messenger.AccountInstance.getInstance(currentAccount),
                    f.getAbsolutePath(), f.getAbsolutePath(), null, null,
                    "application/octet-stream", did, null, null, null, null, null,
                    true, 0, null, null, 0, false);
            Bundle a = new Bundle();
            if (did > 0) {
                a.putLong("user_id", did);
            } else {
                a.putLong("chat_id", -did);
            }
            presentFragment(new ChatActivity(a), true);
            return true;
        });
        presentFragment(picker);
    }

    // Импорт: системный выбор файла .devgram → сравнение/применение в DevGramSettingsIO.
    private void importSettings() {
        try {
            Intent i = new Intent(Intent.ACTION_GET_CONTENT);
            i.setType("*/*");
            i.addCategory(Intent.CATEGORY_OPENABLE);
            startActivityForResult(Intent.createChooser(i, "Выбери файл " + DevGramSettingsIO.EXT), REQ_IMPORT);
        } catch (Throwable e) {
            BulletinFactory.of(this).createErrorBulletin("Не удалось открыть выбор файла").show();
        }
    }

    @Override
    public void onActivityResultFragment(int requestCode, int resultCode, Intent data) {
        if (requestCode == REQ_IMPORT && resultCode == Activity.RESULT_OK && data != null && data.getData() != null) {
            String content = DevGramSettingsIO.readUri(getContext(), data.getData());
            if (content == null) {
                BulletinFactory.of(this).createErrorBulletin("Не удалось прочитать файл").show();
                return;
            }
            DevGramSettingsIO.handleImport(this, content, this::refreshList);
        }
    }

    private void confirmReset() {
        if (getParentActivity() == null) {
            return;
        }
        AlertDialog.Builder b = new AlertDialog.Builder(getParentActivity());
        b.setTitle("Сбросить настройки");
        b.setMessage("Все настройки DevGram вернутся к значениям по умолчанию. Продолжить?");
        b.setPositiveButton("Сбросить", (d, w) -> {
            DevGramConfig.resetToDefaults();
            refreshList();
            BulletinFactory.of(this).createSimpleBulletin(R.raw.contact_check, "Настройки сброшены").show();
        });
        b.setNegativeButton("Отмена", null);
        AlertDialog dlg = b.create();
        showDialog(dlg);
        TextView btn = (TextView) dlg.getButton(AlertDialog.BUTTON_POSITIVE);
        if (btn != null) {
            btn.setTextColor(Theme.getColor(Theme.key_text_RedBold));
        }
    }

    // Удаление аккаунта необратимо — открываем официальный экран Telegram (Конфиденциальность).
    private void confirmDeleteAccount() {
        if (getParentActivity() == null) {
            return;
        }
        AlertDialog.Builder b = new AlertDialog.Builder(getParentActivity());
        b.setTitle("Удалить аккаунт");
        b.setMessage("Удаление аккаунта Telegram необратимо. Откроем официальный раздел «Конфиденциальность», "
                + "где можно управлять удалением аккаунта.");
        b.setPositiveButton("Открыть", (d, w) -> presentFragment(new PrivacySettingsActivity()));
        b.setNegativeButton("Отмена", null);
        AlertDialog dlg = b.create();
        showDialog(dlg);
        TextView btn = (TextView) dlg.getButton(AlertDialog.BUTTON_POSITIVE);
        if (btn != null) {
            btn.setTextColor(Theme.getColor(Theme.key_text_RedBold));
        }
    }

    // Tonkeeper: сперва пытаемся открыть кошелёк напрямую (ton://transfer), иначе — универсальная ссылка.
    private void openTonkeeper() {
        android.app.Activity act = getParentActivity();
        if (act != null) {
            try {
                act.startActivity(new Intent(Intent.ACTION_VIEW,
                        android.net.Uri.parse("ton://transfer/" + TON_ADDRESS)));
                return;
            } catch (Throwable ignore) {
            }
        }
        Browser.openUrl(getContext(), LINK_TONKEEPER);
    }
}

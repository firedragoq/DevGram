package org.telegram.messenger;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

// DevGram: срабатывает, когда пользователь ПОДТВЕРДИЛ закрепление ярлыка-маски
// (см. DevGramDisguise.requestPinShortcut). Только по факту закрепления фиксируем
// маску и прячем оригинальную иконку — если пользователь отклонил диалог, ничего
// не меняется.
public class DevGramDisguiseReceiver extends BroadcastReceiver {

    public static final String ACTION_PINNED = "org.telegram.messenger.DISGUISE_PINNED";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null) return;
        String maskKey = intent.getStringExtra("mask");
        try {
            DevGramDisguise.onShortcutPinned(context.getApplicationContext(), maskKey);
        } catch (Exception e) {
            FileLog.e(e);
        }
    }
}

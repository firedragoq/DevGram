/*
 * DevGram: обработчик кнопки «Восстановить» из уведомления о потере огонька.
 */

package org.telegram.messenger;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import androidx.core.app.NotificationManagerCompat;

public class DevGramStreakReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        long dialogId = intent.getLongExtra("dialogId", 0);
        int account = intent.getIntExtra("account", 0);
        if (dialogId == 0) {
            return;
        }
        DevGramStreaks.restore(account, dialogId);
        try {
            NotificationManagerCompat.from(context).cancel((int) dialogId);
        } catch (Throwable ignore) {
        }
    }
}

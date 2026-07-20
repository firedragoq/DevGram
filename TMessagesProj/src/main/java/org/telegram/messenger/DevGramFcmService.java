package org.telegram.messenger;

import com.google.firebase.messaging.FirebaseMessagingService;
import com.google.firebase.messaging.RemoteMessage;

import org.telegram.tgnet.ConnectionsManager;

// DevGram: FCM через свой rewrite-шлюз (push.ovavpn.fun -> FCM).
// Пуш wake-up: будим соединение, контент приходит по MTProto (как UnifiedPush).
public class DevGramFcmService extends FirebaseMessagingService {

    public static final String GATEWAY = "https://push.ovavpn.fun/";

    @Override
    public void onNewToken(String token) {
        Utilities.globalQueue.postRunnable(() ->
                PushListenerController.sendRegistrationToServer(PushListenerController.PUSH_TYPE_SIMPLE, GATEWAY + token));
    }

    @Override
    public void onMessageReceived(RemoteMessage message) {
        AndroidUtilities.runOnUIThread(() -> {
            ApplicationLoader.postInitApplication();
            Utilities.stageQueue.postRunnable(() -> {
                for (int a = 0; a < UserConfig.MAX_ACCOUNT_COUNT; a++) {
                    if (UserConfig.getInstance(a).isClientActivated()) {
                        ConnectionsManager.onInternalPushReceived(a);
                        ConnectionsManager.getInstance(a).resumeNetworkMaybe();
                    }
                }
            });
        });
    }
}

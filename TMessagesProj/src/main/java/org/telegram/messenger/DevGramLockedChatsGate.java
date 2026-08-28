package org.telegram.messenger;

import android.app.Activity;
import android.content.Context;
import android.text.InputType;
import android.widget.FrameLayout;
import android.widget.Toast;

import androidx.biometric.BiometricManager;
import androidx.biometric.BiometricPrompt;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.FragmentActivity;

import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.LaunchActivity;
import org.telegram.ui.Components.EditTextBoldCursor;
import org.telegram.ui.Components.LayoutHelper;

// DevGram: запрос разблокировки скрытых чатов — биометрия (с фолбэком на пасскод)
// или сразу пасскод. Дёргается при попытке раскрыть скрытые чаты.
public final class DevGramLockedChatsGate {

    public interface Callback {
        void onSuccess();
    }

    private DevGramLockedChatsGate() {}

    public static void prompt(Context ctx, Callback cb) {
        if (cb == null) return;
        // если ничего не настроено — просто пускаем (защиты нет)
        if (!DevGramLockedChats.hasPasscode() && !biometricAvailable(ctx)) {
            cb.onSuccess();
            return;
        }
        FragmentActivity fa = resolveActivity(ctx);
        if (DevGramLockedChats.biometricEnabled() && biometricAvailable(ctx) && fa != null) {
            showBiometric(ctx, fa, cb);
        } else {
            showPasscode(ctx, cb);
        }
    }

    private static boolean biometricAvailable(Context ctx) {
        try {
            int r = BiometricManager.from(ctx).canAuthenticate(
                    BiometricManager.Authenticators.BIOMETRIC_WEAK);
            return r == BiometricManager.BIOMETRIC_SUCCESS;
        } catch (Throwable t) {
            return false;
        }
    }

    private static FragmentActivity resolveActivity(Context ctx) {
        Context c = ctx;
        while (c instanceof android.content.ContextWrapper) {
            if (c instanceof FragmentActivity) return (FragmentActivity) c;
            c = ((android.content.ContextWrapper) c).getBaseContext();
        }
        Activity a = LaunchActivity.instance;
        return a instanceof FragmentActivity ? (FragmentActivity) a : null;
    }

    private static void showBiometric(Context ctx, FragmentActivity fa, Callback cb) {
        try {
            BiometricPrompt prompt = new BiometricPrompt(fa,
                    ContextCompat.getMainExecutor(ctx),
                    new BiometricPrompt.AuthenticationCallback() {
                        @Override
                        public void onAuthenticationSucceeded(BiometricPrompt.AuthenticationResult result) {
                            cb.onSuccess();
                        }

                        @Override
                        public void onAuthenticationError(int errorCode, CharSequence errString) {
                            // отмена/ошибка → предложим пасскод, если он есть
                            if (DevGramLockedChats.hasPasscode()
                                    && errorCode != BiometricPrompt.ERROR_USER_CANCELED
                                    && errorCode != BiometricPrompt.ERROR_NEGATIVE_BUTTON) {
                                // системная ошибка — оставим пользователю ручной путь
                            }
                            if (errorCode == BiometricPrompt.ERROR_NEGATIVE_BUTTON
                                    && DevGramLockedChats.hasPasscode()) {
                                AndroidUtilities.runOnUIThread(() -> showPasscode(ctx, cb));
                            }
                        }
                    });
            BiometricPrompt.PromptInfo.Builder b = new BiometricPrompt.PromptInfo.Builder()
                    .setTitle(LocaleController.getString(R.string.DevGramLockedChats))
                    .setSubtitle(LocaleController.getString(R.string.DevGramLockedChatsUnlockHint))
                    .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_WEAK);
            if (DevGramLockedChats.hasPasscode()) {
                b.setNegativeButtonText(LocaleController.getString(R.string.DevGramLockedChatsUsePasscode));
            } else {
                b.setNegativeButtonText(LocaleController.getString(R.string.Cancel));
            }
            prompt.authenticate(b.build());
        } catch (Throwable t) {
            showPasscode(ctx, cb);
        }
    }

    private static void showPasscode(Context ctx, Callback cb) {
        if (!DevGramLockedChats.hasPasscode()) {
            // нет пасскода, но биометрия недоступна — пускаем
            cb.onSuccess();
            return;
        }
        final EditTextBoldCursor input = new EditTextBoldCursor(ctx);
        input.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_VARIATION_PASSWORD);
        input.setTextSize(android.util.TypedValue.COMPLEX_UNIT_DIP, 18);
        input.setTextColor(Theme.getColor(Theme.key_dialogTextBlack));
        input.setCursorColor(Theme.getColor(Theme.key_dialogTextBlack));
        input.setHintText(LocaleController.getString(R.string.DevGramLockedChatsEnterPasscode));
        input.setHintColor(Theme.getColor(Theme.key_dialogTextHint));
        input.setBackgroundDrawable(Theme.createEditTextDrawable(ctx, true));

        FrameLayout container = new FrameLayout(ctx);
        container.addView(input, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT,
                LayoutHelper.WRAP_CONTENT, 0, 24, 4, 24, 12));

        AlertDialog.Builder builder = new AlertDialog.Builder(ctx);
        builder.setTitle(LocaleController.getString(R.string.DevGramLockedChats));
        builder.setView(container);
        builder.setPositiveButton(LocaleController.getString(R.string.OK), (dialog, which) -> {
            String pin = input.getText() == null ? "" : input.getText().toString();
            if (DevGramLockedChats.checkPasscode(pin)) {
                cb.onSuccess();
            } else {
                if (LaunchActivity.instance != null) {
                    Toast.makeText(ctx, LocaleController.getString(R.string.DevGramLockedChatsWrongPasscode),
                            Toast.LENGTH_SHORT).show();
                }
            }
        });
        builder.setNegativeButton(LocaleController.getString(R.string.Cancel), null);
        AlertDialog dlg = builder.create();
        dlg.show();
        dlg.setOnShowListener(d -> AndroidUtilities.runOnUIThread(() -> {
            input.requestFocus();
            AndroidUtilities.showKeyboard(input);
        }, 80));
    }
}

package org.telegram.ui.Components;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Typeface;
import android.text.InputType;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.inputmethod.EditorInfo;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.Utilities;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.BottomSheet;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Stories.recorder.ButtonWithCounterView;

/**
 * DevGram port of exteraGram's GenerateFromMessageBottomSheet.
 * Lets the user edit the selected message before sending it to AI and choose
 * whether the saved AI conversation should be used as context.
 */
public class DevGramGenerateFromMessageBottomSheet extends BottomSheet {

    public static final class GenerationData {
        public final String prompt;
        public final boolean useHistory;

        public GenerationData(String prompt, boolean useHistory) {
            this.prompt = prompt;
            this.useHistory = useHistory;
        }
    }

    private final EditTextBoldCursor promptField;
    private final OutlineTextContainerView promptFieldContainer;
    private boolean useHistory;

    @SuppressLint("ClickableViewAccessibility")
    public DevGramGenerateFromMessageBottomSheet(
            String initialPrompt,
            BaseFragment parentFragment,
            Context context,
            Utilities.Callback<GenerationData> callback) {
        super(context, true, parentFragment.getResourceProvider());

        fixNavigationBar();
        smoothKeyboardAnimationEnabled = true;
        useHistory = MessagesController.getGlobalMainSettings().getBoolean("dg_aiSaveHistory", true);

        ScrollView scrollView = new ScrollView(context);
        LinearLayout content = new LinearLayout(context);
        content.setPadding(AndroidUtilities.dp(20), 0, AndroidUtilities.dp(20), 0);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setClipChildren(false);
        content.setClipToPadding(false);
        scrollView.addView(content, LayoutHelper.createScroll(
                LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.TOP | Gravity.LEFT));

        FrameLayout promptFrame = new FrameLayout(context);
        promptFrame.setClipChildren(false);
        content.addView(promptFrame, LayoutHelper.createLinear(
                LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        promptFieldContainer = new OutlineTextContainerView(context, resourcesProvider);
        promptFieldContainer.setText("Промпт");
        promptFrame.addView(promptFieldContainer, LayoutHelper.createFrame(
                LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.TOP));

        promptField = new EditTextBoldCursor(context);
        promptField.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
        promptField.setTextColor(getThemedColor(Theme.key_windowBackgroundWhiteBlackText));
        promptField.setHintTextColor(getThemedColor(Theme.key_windowBackgroundWhiteHintText));
        promptField.setBackground(null);
        promptField.setMaxLines(8);
        promptField.setTypeface(Typeface.DEFAULT);
        if (!TextUtils.isEmpty(initialPrompt)) {
            promptField.setText(initialPrompt);
            promptField.setSelection(promptField.length());
        }
        promptField.setInputType(InputType.TYPE_CLASS_TEXT
                | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
                | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        promptField.setImeOptions(EditorInfo.IME_ACTION_DONE | EditorInfo.IME_FLAG_NO_EXTRACT_UI);
        promptField.setCursorColor(getThemedColor(Theme.key_windowBackgroundWhiteInputFieldActivated));
        promptField.setCursorWidth(1.5f);
        promptField.setCursorSize(AndroidUtilities.dp(20));
        promptField.setGravity(LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT);
        promptField.setOnFocusChangeListener((view, focused) ->
                promptFieldContainer.animateSelection(focused));
        promptField.setOnTouchListener((view, event) -> {
            view.getParent().requestDisallowInterceptTouchEvent(true);
            if ((event.getAction() & MotionEvent.ACTION_MASK) == MotionEvent.ACTION_UP
                    || (event.getAction() & MotionEvent.ACTION_MASK) == MotionEvent.ACTION_CANCEL) {
                view.getParent().requestDisallowInterceptTouchEvent(false);
            }
            return false;
        });
        int horizontalPadding = AndroidUtilities.dp(16);
        promptField.setPadding(horizontalPadding, 0, horizontalPadding, 0);
        promptFieldContainer.addView(promptField, LayoutHelper.createFrame(
                LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT,
                Gravity.TOP, 0, 16, 0, 16));
        promptFieldContainer.attachEditText(promptField);

        // exteraGram only offers per-request history when global AI history is enabled.
        if (useHistory) {
            CheckBox2 historyCheckBox = new CheckBox2(context, 21, resourcesProvider);
            historyCheckBox.setColor(
                    Theme.key_radioBackgroundChecked,
                    Theme.key_checkboxDisabled,
                    Theme.key_checkboxCheck);
            historyCheckBox.setDrawUnchecked(true);
            historyCheckBox.setChecked(true, false);
            historyCheckBox.setDrawBackgroundAsArc(10);

            TextView historyText = new TextView(context);
            historyText.setTextColor(getThemedColor(Theme.key_windowBackgroundWhiteBlackText));
            historyText.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
            historyText.setText("История сообщений");

            FrameLayout checkFrame = new FrameLayout(context);
            checkFrame.addView(historyCheckBox, LayoutHelper.createFrame(
                    21, 21, Gravity.LEFT | Gravity.CENTER_VERTICAL));

            LinearLayout historyRow = new LinearLayout(context);
            historyRow.setOrientation(LinearLayout.HORIZONTAL);
            historyRow.setGravity(Gravity.CENTER_VERTICAL);
            historyRow.setPadding(
                    AndroidUtilities.dp(8), AndroidUtilities.dp(6),
                    AndroidUtilities.dp(10), AndroidUtilities.dp(6));
            historyRow.addView(checkFrame, LayoutHelper.createLinear(
                    24, 24, Gravity.CENTER_VERTICAL, 0, 0, 6, 0));
            historyRow.addView(historyText, LayoutHelper.createLinear(
                    LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_VERTICAL));
            historyRow.setOnClickListener(view -> {
                historyCheckBox.setChecked(!historyCheckBox.isChecked(), true);
                useHistory = historyCheckBox.isChecked();
            });
            ScaleStateListAnimator.apply(historyRow, 0.05f, 1.2f);
            historyRow.setBackground(Theme.createRadSelectorDrawable(
                    getThemedColor(Theme.key_listSelector), 6, 6));
            LinearLayout optionsRow = new LinearLayout(context);
            optionsRow.setOrientation(LinearLayout.HORIZONTAL);
            optionsRow.setGravity(Gravity.LEFT);
            optionsRow.addView(historyRow, LayoutHelper.createLinear(
                    LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT));
            content.addView(optionsRow, LayoutHelper.createLinear(
                    LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT,
                    Gravity.CENTER_HORIZONTAL, 0, 16, 0, 0));
        }

        ButtonWithCounterView continueButton = new ButtonWithCounterView(context, resourcesProvider).setRound();
        continueButton.setColor(getThemedColor(Theme.key_featuredStickers_addButton));
        continueButton.setText("Продолжить", false);
        continueButton.setOnClickListener(view -> {
            String prompt = promptField.getText().toString().trim();
            if (TextUtils.isEmpty(prompt)) {
                AndroidUtilities.shakeViewSpring(promptFieldContainer);
                view.performHapticFeedback(0);
                return;
            }
            dismiss();
            callback.run(new GenerationData(prompt, useHistory));
        });
        content.addView(continueButton, LayoutHelper.createLinear(
                LayoutHelper.MATCH_PARENT, 48, 0, 0, 16, 0, 16));

        setCustomView(scrollView);
        setTitle("Генерировать", true);
    }
}

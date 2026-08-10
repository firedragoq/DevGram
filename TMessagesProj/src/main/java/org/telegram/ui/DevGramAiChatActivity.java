package org.telegram.ui;

import android.content.Context;
import android.graphics.Typeface;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.DevGramAiClient;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.LayoutHelper;

public class DevGramAiChatActivity extends BaseFragment {
    private TextView conversation;
    private EditText input;
    private Button send;

    @Override
    public View createView(Context context) {
        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setTitle("ИИ-чат");
        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override public void onItemClick(int id) { if (id == -1) finishFragment(); }
        });
        FrameLayout root = new FrameLayout(context);
        LinearLayout column = new LinearLayout(context);
        column.setOrientation(LinearLayout.VERTICAL);
        column.setPadding(AndroidUtilities.dp(16), AndroidUtilities.dp(12), AndroidUtilities.dp(16), AndroidUtilities.dp(12));
        ScrollView scroll = new ScrollView(context);
        conversation = new TextView(context);
        conversation.setTextSize(16);
        conversation.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
        conversation.setText("DevGram AI\n\nЗадайте вопрос или вставьте текст для редактирования и пересказа.");
        conversation.setPadding(0, 0, 0, AndroidUtilities.dp(16));
        scroll.addView(conversation, new ScrollView.LayoutParams(-1, -2));
        column.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        input = new EditText(context);
        input.setHint("Сообщение");
        input.setMinLines(1);
        input.setMaxLines(5);
        column.addView(input, new LinearLayout.LayoutParams(-1, -2));
        send = new Button(context);
        send.setText("Отправить");
        send.setTypeface(Typeface.DEFAULT_BOLD);
        send.setOnClickListener(v -> send());
        column.addView(send, new LinearLayout.LayoutParams(-1, AndroidUtilities.dp(48)));
        root.addView(column, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.FILL));
        return fragmentView = root;
    }

    private void send() {
        String prompt = input.getText().toString().trim();
        if (prompt.isEmpty() || send == null) return;
        input.setText("");
        conversation.append("\n\nВы:\n" + prompt + "\n\nИИ:\n…");
        send.setEnabled(false);
        DevGramAiClient.generate(prompt, (answer, error) -> {
            if (conversation == null) return;
            CharSequence old = conversation.getText();
            String text = old.toString();
            if (text.endsWith("…")) text = text.substring(0, text.length() - 1);
            conversation.setText(text + (error == null ? answer : "Ошибка: " + error.getMessage()));
            if (send != null) send.setEnabled(true);
        });
    }
}

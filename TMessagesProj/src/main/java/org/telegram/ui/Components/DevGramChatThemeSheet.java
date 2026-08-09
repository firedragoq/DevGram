package org.telegram.ui.Components;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.SparseIntArray;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessagesController;
import org.telegram.ui.ActionBar.BottomSheet;
import org.telegram.ui.ActionBar.Theme;

import java.io.File;
import java.util.ArrayList;

// DevGram: выбор ЛОКАЛЬНОЙ темы этого чата — горизонтальные карточки-превью (как у exteraGram).
public class DevGramChatThemeSheet extends BottomSheet {

    public interface OnPick {
        void pick(String themeNameOrNull);
    }

    private final long dialogId;
    private final OnPick onPick;
    private String selectedName;
    private final ArrayList<ThemeCard> cards = new ArrayList<>();

    public DevGramChatThemeSheet(Context context, long dialogId, String currentName,
                                 Theme.ResourcesProvider resourcesProvider, OnPick onPick) {
        super(context, false, resourcesProvider);
        this.dialogId = dialogId;
        this.selectedName = currentName;
        this.onPick = onPick;
        fixNavigationBar(getThemedColor(Theme.key_dialogBackground));

        LinearLayout root = new LinearLayout(context);
        root.setOrientation(LinearLayout.VERTICAL);

        TextView title = new TextView(context);
        title.setText("Тема этого чата");
        title.setTextColor(getThemedColor(Theme.key_dialogTextBlack));
        title.setTextSize(20);
        title.setTypeface(AndroidUtilities.bold());
        title.setPadding(AndroidUtilities.dp(22), AndroidUtilities.dp(18), AndroidUtilities.dp(22), AndroidUtilities.dp(6));
        root.addView(title, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        RecyclerListView listView = new RecyclerListView(context, resourcesProvider);
        androidx.recyclerview.widget.LinearLayoutManager lm =
                new androidx.recyclerview.widget.LinearLayoutManager(context, androidx.recyclerview.widget.LinearLayoutManager.HORIZONTAL, false);
        listView.setLayoutManager(lm);
        listView.setPadding(AndroidUtilities.dp(12), AndroidUtilities.dp(6), AndroidUtilities.dp(12), AndroidUtilities.dp(16));
        listView.setClipToPadding(false);

        final ArrayList<Item> items = buildItems(context);
        listView.setAdapter(new RecyclerListView.SelectionAdapter() {
            @Override
            public boolean isEnabled(androidx.recyclerview.widget.RecyclerView.ViewHolder holder) {
                return true;
            }

            @Override
            public androidx.recyclerview.widget.RecyclerView.ViewHolder onCreateViewHolder(android.view.ViewGroup parent, int viewType) {
                ThemeCard card = new ThemeCard(context);
                cards.add(card);
                return new RecyclerListView.Holder(card);
            }

            @Override
            public void onBindViewHolder(androidx.recyclerview.widget.RecyclerView.ViewHolder holder, int position) {
                ((ThemeCard) holder.itemView).bind(items.get(position));
            }

            @Override
            public int getItemCount() {
                return items.size();
            }
        });
        listView.setOnItemClickListener((view, position) -> {
            Item it = items.get(position);
            selectedName = it.name;
            for (ThemeCard c : cards) {
                c.invalidate();
            }
            if (onPick != null) {
                onPick.pick(it.name);
            }
            AndroidUtilities.runOnUIThread(this::dismiss, 180);
        });
        root.addView(listView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 130));

        setCustomView(root);
    }

    private ArrayList<Item> buildItems(Context context) {
        ArrayList<Item> items = new ArrayList<>();
        items.add(new Item(null, "По умолчанию", null)); // сброс
        if (Theme.themes != null) {
            for (Theme.ThemeInfo t : Theme.themes) {
                if (t == null || android.text.TextUtils.isEmpty(t.getName())) {
                    continue;
                }
                items.add(new Item(t.getName(), t.getName(), t));
            }
        }
        return items;
    }

    private static class Item {
        final String name;      // null = сброс
        final String label;
        final Theme.ThemeInfo theme;
        Item(String name, String label, Theme.ThemeInfo theme) {
            this.name = name;
            this.label = label;
            this.theme = theme;
        }
    }

    private class ThemeCard extends FrameLayout {
        private final Preview preview;
        private final TextView label;
        private Item item;

        ThemeCard(Context context) {
            super(context);
            LinearLayout col = new LinearLayout(context);
            col.setOrientation(LinearLayout.VERTICAL);
            col.setGravity(Gravity.CENTER_HORIZONTAL);
            preview = new Preview(context);
            col.addView(preview, LayoutHelper.createLinear(76, 96));
            label = new TextView(context);
            label.setTextSize(12);
            label.setMaxLines(1);
            label.setGravity(Gravity.CENTER);
            label.setTextColor(getThemedColor(Theme.key_dialogTextBlack));
            col.addView(label, LayoutHelper.createLinear(84, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL, 0, 4, 0, 0));
            addView(col, LayoutHelper.createFrame(84, LayoutHelper.WRAP_CONTENT, Gravity.CENTER, 6, 0, 6, 0));
        }

        void bind(Item it) {
            this.item = it;
            label.setText(it.label);
            preview.item = it;
            preview.invalidate();
        }

        private class Preview extends View {
            Item item;
            private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
            private final Paint border = new Paint(Paint.ANTI_ALIAS_FLAG);
            private final RectF r = new RectF();

            Preview(Context context) {
                super(context);
                border.setStyle(Paint.Style.STROKE);
                border.setStrokeWidth(AndroidUtilities.dp(2));
            }

            @Override
            protected void onDraw(Canvas canvas) {
                float w = getWidth(), h = getHeight();
                float rad = AndroidUtilities.dp(10);
                int bg, in, out, accent;
                if (item.theme == null) {
                    bg = getThemedColor(Theme.key_windowBackgroundWhite);
                    in = getThemedColor(Theme.key_chat_inBubble);
                    out = getThemedColor(Theme.key_chat_outBubble);
                    accent = getThemedColor(Theme.key_actionBarDefault);
                } else {
                    SparseIntArray c = getColors(item.theme);
                    bg = pick(c, Theme.key_chat_wallpaper, pick(c, Theme.key_windowBackgroundWhite, 0xFFFFFFFF));
                    in = pick(c, Theme.key_chat_inBubble, 0xFFFFFFFF);
                    out = pick(c, Theme.key_chat_outBubble, 0xFF3A8FE0);
                    accent = pick(c, Theme.key_actionBarDefault, out);
                }
                // фон
                p.setColor(bg);
                r.set(0, 0, w, h);
                canvas.drawRoundRect(r, rad, rad, p);
                // шапка (accent)
                p.setColor(accent);
                canvas.save();
                canvas.clipRect(0, 0, w, AndroidUtilities.dp(16));
                canvas.drawRoundRect(r, rad, rad, p);
                canvas.restore();
                // входящий пузырь
                p.setColor(in);
                r.set(AndroidUtilities.dp(8), AndroidUtilities.dp(30), w * 0.72f, AndroidUtilities.dp(44));
                canvas.drawRoundRect(r, AndroidUtilities.dp(7), AndroidUtilities.dp(7), p);
                // исходящий пузырь
                p.setColor(out);
                r.set(w * 0.32f, AndroidUtilities.dp(54), w - AndroidUtilities.dp(8), AndroidUtilities.dp(68));
                canvas.drawRoundRect(r, AndroidUtilities.dp(7), AndroidUtilities.dp(7), p);
                // рамка выбора
                boolean selected = item.name == null ? selectedName == null : item.name.equals(selectedName);
                if (selected) {
                    border.setColor(getThemedColor(Theme.key_dialogTextBlue));
                    r.set(AndroidUtilities.dp(1), AndroidUtilities.dp(1), w - AndroidUtilities.dp(1), h - AndroidUtilities.dp(1));
                    canvas.drawRoundRect(r, rad, rad, border);
                }
            }
        }
    }

    private final java.util.HashMap<String, SparseIntArray> colorCache = new java.util.HashMap<>();
    private SparseIntArray getColors(Theme.ThemeInfo t) {
        SparseIntArray c = colorCache.get(t.getName());
        if (c == null) {
            try {
                c = Theme.getThemeFileValues(t.pathToFile != null ? new File(t.pathToFile) : null, t.assetName, null);
            } catch (Throwable e) {
                c = new SparseIntArray();
            }
            colorCache.put(t.getName(), c);
        }
        return c;
    }

    private static int pick(SparseIntArray c, int key, int def) {
        int idx = c.indexOfKey(key);
        return idx >= 0 ? c.valueAt(idx) : def;
    }
}

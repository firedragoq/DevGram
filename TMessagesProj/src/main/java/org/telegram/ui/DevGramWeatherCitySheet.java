package org.telegram.ui;

import android.content.Context;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.TextView;

import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.ui.ActionBar.BottomSheet;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.RecyclerListView;
import org.telegram.ui.Stories.recorder.Weather;

import java.util.ArrayList;
import java.util.List;

// DevGram: поиск города для виджета погоды. Пользователь вводит название — снизу
// появляется список найденных городов, тап по нужному сохраняет его (с координатами),
// чтобы погода бралась без постоянной геолокации.
public class DevGramWeatherCitySheet extends BottomSheet {

    public interface OnChosen {
        void run();
    }

    private final OnChosen onChosen;
    private final List<Weather.CityResult> results = new ArrayList<>();
    private final Adapter adapter;
    private final TextView hintView;
    private String lastQuery = "";
    private final Runnable searchRunnable;

    public DevGramWeatherCitySheet(Context context, OnChosen onChosen) {
        super(context, true); // needFocus=true — окно принимает ввод, клавиатура открывается
        this.onChosen = onChosen;
        smoothKeyboardAnimationEnabled = true;
        fixNavigationBar();

        FrameLayout container = new FrameLayout(context);

        TextView title = new TextView(context);
        title.setTextColor(getThemedColor(Theme.key_dialogTextBlack));
        title.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 20);
        title.setTypeface(AndroidUtilities.bold());
        title.setText("Город погоды");
        title.setPadding(AndroidUtilities.dp(22), AndroidUtilities.dp(16), AndroidUtilities.dp(22), 0);
        container.addView(title, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.TOP));

        EditText editText = new EditText(context);
        editText.setTextColor(getThemedColor(Theme.key_dialogTextBlack));
        editText.setHintTextColor(getThemedColor(Theme.key_dialogTextHint));
        editText.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
        editText.setHint("Введите город…");
        editText.setBackgroundDrawable(Theme.createRoundRectDrawable(AndroidUtilities.dp(10),
                getThemedColor(Theme.key_dialogSearchBackground)));
        editText.setPadding(AndroidUtilities.dp(14), AndroidUtilities.dp(12), AndroidUtilities.dp(14), AndroidUtilities.dp(12));
        editText.setSingleLine(true);
        editText.setImeOptions(EditorInfo.IME_ACTION_SEARCH);
        container.addView(editText, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT,
                Gravity.TOP, 16, 52, 16, 0));

        hintView = new TextView(context);
        hintView.setTextColor(getThemedColor(Theme.key_dialogTextGray3));
        hintView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        hintView.setGravity(Gravity.CENTER);
        hintView.setText("Начните вводить название города");
        container.addView(hintView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT,
                Gravity.TOP, 22, 130, 22, 0));

        RecyclerListView listView = new RecyclerListView(context);
        listView.setLayoutManager(new LinearLayoutManager(context));
        adapter = new Adapter();
        listView.setAdapter(adapter);
        listView.setPadding(0, AndroidUtilities.dp(112), 0, AndroidUtilities.dp(8));
        listView.setClipToPadding(false);
        listView.setOnItemClickListener((view, position) -> {
            if (position < 0 || position >= results.size()) return;
            Weather.CityResult r = results.get(position);
            Weather.setManualCity(r.title, r.lat, r.lng);
            if (onChosen != null) onChosen.run();
            dismiss();
        });
        container.addView(listView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 360, Gravity.TOP));

        searchRunnable = () -> doSearch(lastQuery);
        editText.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) {}
            @Override public void afterTextChanged(Editable s) {
                lastQuery = s.toString().trim();
                AndroidUtilities.cancelRunOnUIThread(searchRunnable);
                if (lastQuery.length() < 2) {
                    results.clear();
                    adapter.notifyDataSetChanged();
                    hintView.setText("Начните вводить название города");
                    hintView.setVisibility(View.VISIBLE);
                    return;
                }
                hintView.setText("Поиск…");
                hintView.setVisibility(View.VISIBLE);
                AndroidUtilities.runOnUIThread(searchRunnable, 450); // дебаунс
            }
        });

        setCustomView(container);
        AndroidUtilities.runOnUIThread(() -> {
            editText.requestFocus();
            AndroidUtilities.showKeyboard(editText);
        }, 100);
    }

    private void doSearch(String query) {
        if (query.length() < 2) return;
        Weather.searchCity(query, list -> {
            if (!query.equals(lastQuery)) return; // устаревший ответ
            results.clear();
            results.addAll(list);
            adapter.notifyDataSetChanged();
            hintView.setVisibility(results.isEmpty() ? View.VISIBLE : View.GONE);
            if (results.isEmpty()) hintView.setText("Ничего не найдено");
        });
    }

    private class Adapter extends RecyclerListView.SelectionAdapter {
        @Override
        public boolean isEnabled(RecyclerView.ViewHolder holder) { return true; }

        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            return new RecyclerListView.Holder(new CityRow(parent.getContext()));
        }

        @Override
        public void onBindViewHolder(RecyclerView.ViewHolder holder, int position) {
            ((CityRow) holder.itemView).bind(results.get(position));
        }

        @Override
        public int getItemCount() { return results.size(); }
    }

    // Строка результата: название города + регион/страна.
    private class CityRow extends FrameLayout {
        private final TextView titleView, subtitleView;

        CityRow(Context context) {
            super(context);
            setBackgroundDrawable(Theme.getSelectorDrawable(false));
            setPadding(AndroidUtilities.dp(20), AndroidUtilities.dp(8), AndroidUtilities.dp(20), AndroidUtilities.dp(8));

            titleView = new TextView(context);
            titleView.setTextColor(getThemedColor(Theme.key_dialogTextBlack));
            titleView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
            titleView.setMaxLines(1);
            addView(titleView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT,
                    Gravity.TOP, 0, 2, 0, 0));

            subtitleView = new TextView(context);
            subtitleView.setTextColor(getThemedColor(Theme.key_dialogTextGray3));
            subtitleView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
            subtitleView.setMaxLines(1);
            addView(subtitleView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT,
                    Gravity.TOP, 0, 24, 0, 0));

            setLayoutParams(new RecyclerView.LayoutParams(LayoutHelper.MATCH_PARENT, AndroidUtilities.dp(52)));
        }

        void bind(Weather.CityResult r) {
            titleView.setText(r.title);
            subtitleView.setText(r.subtitle);
            subtitleView.setVisibility(r.subtitle == null || r.subtitle.isEmpty() ? GONE : VISIBLE);
        }
    }
}

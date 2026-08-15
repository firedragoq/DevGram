package org.telegram.ui.pillstack.pills;

import org.telegram.ui.pillstack.PillStackConfig;

import android.annotation.SuppressLint;
import android.content.Context;

import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.Theme;

/** DevGram: GRAM/TON-виджет (цена 1 TON в целевой валюте). */
@SuppressLint("ViewConstructor")
public class GramPill extends RatePill {
    private static final RateCache CACHE = new RateCache();

    public GramPill(Context context, Theme.ResourcesProvider rp) {
        super(context, rp, CACHE, "TON", 3, R.drawable.mini_gram_16, new ColoredBackground());
    }

    @Override public int getPillId() { return PillStackConfig.GRAM; }
    @Override public String getTargetSelection() { return PillStackConfig.getGramTargetCurrency(); }
    @Override public void setTargetSelection(String selection) { PillStackConfig.setGramTargetCurrency(selection); }
}

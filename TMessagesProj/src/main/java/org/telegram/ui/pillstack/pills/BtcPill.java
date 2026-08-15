package org.telegram.ui.pillstack.pills;

import org.telegram.ui.pillstack.PillStackConfig;

import android.annotation.SuppressLint;
import android.content.Context;

import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.Theme;

/** DevGram: BTC-виджет (цена 1 BTC в целевой валюте). */
@SuppressLint("ViewConstructor")
public class BtcPill extends RatePill {
    private static final RateCache CACHE = new RateCache();

    public BtcPill(Context context, Theme.ResourcesProvider rp) {
        super(context, rp, CACHE, "BTC", 2, R.drawable.pillstack_btc, new ColoredBackground(-1071598, -1608430));
    }

    @Override public int getPillId() { return PillStackConfig.BTC; }
    @Override public String getTargetSelection() { return PillStackConfig.getBtcTargetCurrency(); }
    @Override public void setTargetSelection(String selection) { PillStackConfig.setBtcTargetCurrency(selection); }
}

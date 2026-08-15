package org.telegram.ui.pillstack.pills;

import org.telegram.ui.pillstack.PillStackConfig;

import android.annotation.SuppressLint;
import android.content.Context;

import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.Theme;

/** DevGram: USD-виджет (курс 1 USD в целевой валюте). */
@SuppressLint("ViewConstructor")
public class UsdPill extends RatePill {
    private static final RateCache CACHE = new RateCache();

    public UsdPill(Context context, Theme.ResourcesProvider rp) {
        super(context, rp, CACHE, "USD", 2, R.drawable.pillstack_usd, new ColoredBackground(-14840995, -15172775));
    }

    @Override public int getPillId() { return PillStackConfig.USD; }

    @Override
    public String getTargetSelection() {
        if ("USD".equalsIgnoreCase(PillStackConfig.getUsdTargetCurrency())) return "AUTO";
        return PillStackConfig.getUsdTargetCurrency();
    }

    @Override public void setTargetSelection(String selection) { PillStackConfig.setUsdTargetCurrency(selection); }

    @Override public String[] getTargetCurrencies() { return PillStackCurrencies.getTargetCurrencies("USD"); }
}

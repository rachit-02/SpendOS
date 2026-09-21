package com.spendos.insights.detectors;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.NumberFormat;
import java.util.Currency;
import java.util.Locale;

/** Formatting for insight sentences, e.g. "₹1,860". Amounts are rounded to whole units for readability. */
final class Texts {

    private Texts() {
    }

    static String money(BigDecimal amount, String currency) {
        NumberFormat format = NumberFormat.getCurrencyInstance("INR".equals(currency) ? new Locale("en", "IN") : Locale.US);
        try {
            format.setCurrency(Currency.getInstance(currency));
        } catch (IllegalArgumentException ignored) {
            // keep the locale default
        }
        format.setMaximumFractionDigits(0);
        format.setMinimumFractionDigits(0);
        return format.format(amount.setScale(0, RoundingMode.HALF_UP));
    }

    static String percent(BigDecimal value) {
        return value.setScale(0, RoundingMode.HALF_UP).toPlainString() + "%";
    }

    static String ratio(BigDecimal value) {
        BigDecimal rounded = value.setScale(1, RoundingMode.HALF_UP).stripTrailingZeros();
        return rounded.toPlainString() + "x";
    }
}

package com.spendos.common.util;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.NumberFormat;
import java.util.Currency;
import java.util.Locale;

/** Money and percentages in sentences, e.g. "₹8,400" and "+35.5%". Amounts are whole units for readability. */
public final class MoneyText {

    private MoneyText() {
    }

    public static String money(BigDecimal amount, String currency) {
        NumberFormat format = NumberFormat.getCurrencyInstance("INR".equals(currency) ? new Locale("en", "IN") : Locale.US);
        try {
            format.setCurrency(Currency.getInstance(currency));
        } catch (IllegalArgumentException ignored) {
            // keep the locale default
        }
        format.setMaximumFractionDigits(0);
        format.setMinimumFractionDigits(0);
        return format.format(Money.nz(amount).setScale(0, RoundingMode.HALF_UP));
    }

    /** "+₹2,200" or "-₹1,200". */
    public static String signedMoney(BigDecimal amount, String currency) {
        BigDecimal value = Money.nz(amount);
        return (value.signum() > 0 ? "+" : value.signum() < 0 ? "-" : "") + money(value.abs(), currency);
    }

    /** "+35.5%" or "-4.7%" (one decimal, trailing ".0" dropped). */
    public static String signedPercent(BigDecimal value) {
        BigDecimal rounded = value.setScale(1, RoundingMode.HALF_UP);
        String text = rounded.stripTrailingZeros().abs().toPlainString();
        return (rounded.signum() > 0 ? "+" : rounded.signum() < 0 ? "-" : "") + text + "%";
    }

    public static String percent(BigDecimal value) {
        return value.setScale(1, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString() + "%";
    }
}

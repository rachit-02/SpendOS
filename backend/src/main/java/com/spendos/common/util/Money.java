package com.spendos.common.util;

import java.math.BigDecimal;
import java.math.RoundingMode;

/** Money and percentage helpers. All financial math uses BigDecimal, never floating point. */
public final class Money {

    public static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    private Money() {
    }

    public static BigDecimal scale(BigDecimal value) {
        return nz(value).setScale(2, RoundingMode.HALF_UP);
    }

    public static BigDecimal nz(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    /** part / whole * 100, rounded to one decimal; zero when whole is zero. */
    public static BigDecimal percent(BigDecimal part, BigDecimal whole) {
        if (whole == null || whole.signum() == 0) {
            return BigDecimal.ZERO.setScale(1);
        }
        return nz(part).multiply(HUNDRED).divide(whole, 1, RoundingMode.HALF_UP);
    }

    /** Percentage change from previous to current, one decimal; null when previous is zero. */
    public static BigDecimal percentChange(BigDecimal current, BigDecimal previous) {
        if (previous == null || previous.signum() == 0) {
            return null;
        }
        return nz(current).subtract(previous).multiply(HUNDRED).divide(previous, 1, RoundingMode.HALF_UP);
    }

    /** part / whole as a ratio with three decimals (e.g. savings rate 0.436); zero when whole is zero. */
    public static BigDecimal ratio(BigDecimal part, BigDecimal whole) {
        if (whole == null || whole.signum() == 0) {
            return BigDecimal.ZERO.setScale(3);
        }
        return nz(part).divide(whole, 3, RoundingMode.HALF_UP);
    }
}

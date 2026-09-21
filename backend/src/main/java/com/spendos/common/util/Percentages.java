package com.spendos.common.util;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.IntStream;

/** Rounds shares so they always add up to exactly 100 (largest remainder method). */
public final class Percentages {

    private Percentages() {
    }

    /** Percentages of {@code parts} with one decimal place that sum to exactly 100.0 (or all zero). */
    public static List<BigDecimal> ofTotal(List<BigDecimal> parts) {
        BigDecimal total = parts.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        if (total.signum() == 0) {
            return parts.stream().map(p -> BigDecimal.ZERO.setScale(1)).toList();
        }
        // Work in tenths of a percent: 1000 units in total.
        List<BigDecimal> exact = parts.stream()
                .map(p -> p.multiply(BigDecimal.valueOf(1000)).divide(total, 10, RoundingMode.HALF_UP))
                .toList();
        List<Long> floors = new ArrayList<>(exact.stream().map(e -> e.setScale(0, RoundingMode.FLOOR).longValue()).toList());
        long remaining = 1000 - floors.stream().mapToLong(Long::longValue).sum();
        List<Integer> byRemainder = IntStream.range(0, parts.size()).boxed()
                .sorted(Comparator.comparing((Integer i) -> exact.get(i).subtract(BigDecimal.valueOf(floors.get(i))))
                        .reversed())
                .toList();
        for (int k = 0; k < remaining; k++) {
            int index = byRemainder.get(k % byRemainder.size());
            floors.set(index, floors.get(index) + 1);
        }
        return floors.stream().map(units -> BigDecimal.valueOf(units, 1)).toList();
    }
}

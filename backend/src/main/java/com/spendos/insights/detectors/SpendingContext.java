package com.spendos.insights.detectors;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;

/**
 * Everything the detectors need for one user and month: this month's spending (up to today for the
 * current month) and up to six previous months as the user's own baseline. Detectors never compare a
 * user with other people.
 */
public record SpendingContext(
        UUID userId,
        YearMonth month,
        LocalDate periodStart,
        LocalDate periodEnd,
        BigDecimal progress,
        List<Tx> current,
        List<Tx> history,
        List<YearMonth> baselineMonths,
        BigDecimal currentIncome,
        String currencyCode,
        BigDecimal recurringMonthlyCost,
        int recurringCount) {

    public static final MathContext MC = new MathContext(10, RoundingMode.HALF_UP);

    /** A spending transaction reduced to what detectors need. */
    public record Tx(UUID id, LocalDate date, BigDecimal amount, UUID categoryId, String categoryName, UUID merchantId,
                     String merchantName) {
    }

    public boolean inProgress() {
        return progress.compareTo(BigDecimal.ONE) < 0;
    }

    public BigDecimal currentExpense() {
        return sum(current);
    }

    /** Monthly totals per key across the baseline months (missing months count as zero). */
    public <K> Map<K, List<BigDecimal>> baselineByMonth(Function<Tx, K> key) {
        Map<K, Map<YearMonth, BigDecimal>> totals = new HashMap<>();
        for (Tx tx : history) {
            K k = key.apply(tx);
            if (k == null) {
                continue;
            }
            totals.computeIfAbsent(k, x -> new HashMap<>()).merge(YearMonth.from(tx.date()), tx.amount(), BigDecimal::add);
        }
        Map<K, List<BigDecimal>> result = new HashMap<>();
        totals.forEach((k, byMonth) -> {
            List<BigDecimal> values = new ArrayList<>();
            for (YearMonth month : baselineMonths) {
                values.add(byMonth.getOrDefault(month, BigDecimal.ZERO));
            }
            result.put(k, values);
        });
        return result;
    }

    public <K> Map<K, List<Tx>> currentBy(Function<Tx, K> key) {
        Map<K, List<Tx>> grouped = new LinkedHashMap<>();
        for (Tx tx : current) {
            K k = key.apply(tx);
            if (k != null) {
                grouped.computeIfAbsent(k, x -> new ArrayList<>()).add(tx);
            }
        }
        return grouped;
    }

    public List<BigDecimal> baselineTotals() {
        Map<YearMonth, BigDecimal> byMonth = new HashMap<>();
        history.forEach(tx -> byMonth.merge(YearMonth.from(tx.date()), tx.amount(), BigDecimal::add));
        return baselineMonths.stream().map(m -> byMonth.getOrDefault(m, BigDecimal.ZERO)).toList();
    }

    public static BigDecimal sum(List<Tx> transactions) {
        return transactions.stream().map(Tx::amount).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    public static BigDecimal mean(List<BigDecimal> values) {
        if (values.isEmpty()) {
            return BigDecimal.ZERO;
        }
        return values.stream().reduce(BigDecimal.ZERO, BigDecimal::add).divide(BigDecimal.valueOf(values.size()), MC);
    }

    public static BigDecimal standardDeviation(List<BigDecimal> values) {
        if (values.size() < 2) {
            return BigDecimal.ZERO;
        }
        BigDecimal mean = mean(values);
        BigDecimal variance = values.stream().map(v -> v.subtract(mean).pow(2)).reduce(BigDecimal.ZERO, BigDecimal::add)
                .divide(BigDecimal.valueOf(values.size()), MC);
        return variance.sqrt(MC);
    }

    public static BigDecimal median(List<BigDecimal> values) {
        if (values.isEmpty()) {
            return BigDecimal.ZERO;
        }
        List<BigDecimal> sorted = values.stream().sorted(Comparator.naturalOrder()).toList();
        int middle = sorted.size() / 2;
        return sorted.size() % 2 == 1 ? sorted.get(middle)
                : sorted.get(middle - 1).add(sorted.get(middle)).divide(BigDecimal.valueOf(2), MC);
    }
}

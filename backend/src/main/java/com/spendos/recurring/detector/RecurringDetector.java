package com.spendos.recurring.detector;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Finds recurring payments in a user's spending. Payments are grouped by merchant; the median gap
 * between payments decides the frequency band, and confidence combines
 * <ul>
 *   <li>interval regularity (share of gaps inside the band) - weight 0.5,</li>
 *   <li>amount consistency (coefficient of variation; 5% or less is fully consistent) - weight 0.3,</li>
 *   <li>history length (6+ occurrences is full credit) - weight 0.2.</li>
 * </ul>
 * Only groups with confidence of at least {@link #MIN_CONFIDENCE} are reported. Pure: no I/O.
 */
public final class RecurringDetector {

    public static final BigDecimal MIN_CONFIDENCE = new BigDecimal("0.60");
    private static final MathContext MC = new MathContext(10, RoundingMode.HALF_UP);

    /** Frequency bands: nominal gap, accepted gap range and minimum occurrences. */
    public enum Frequency {
        WEEKLY("weekly", 7, 5, 9, 4),
        BIWEEKLY("biweekly", 14, 12, 16, 3),
        MONTHLY("monthly", 30, 26, 35, 3),
        QUARTERLY("quarterly", 91, 80, 100, 2),
        ANNUAL("annual", 365, 345, 385, 2);

        public final String code;
        final int nominalDays;
        final int minDays;
        final int maxDays;
        final int minOccurrences;

        Frequency(String code, int nominalDays, int minDays, int maxDays, int minOccurrences) {
            this.code = code;
            this.nominalDays = nominalDays;
            this.minDays = minDays;
            this.maxDays = maxDays;
            this.minOccurrences = minOccurrences;
        }

        boolean accepts(long days) {
            return days >= minDays && days <= maxDays;
        }

        public LocalDate next(LocalDate last) {
            return switch (this) {
                case MONTHLY -> last.plusMonths(1);
                case QUARTERLY -> last.plusMonths(3);
                case ANNUAL -> last.plusYears(1);
                default -> last.plusDays(nominalDays);
            };
        }

        /** Payments not seen for this long after the expected date are treated as cancelled. */
        public int graceDays() {
            return Math.max(5, (maxDays - nominalDays) * 2);
        }

        static Frequency classify(long medianDays) {
            for (Frequency frequency : values()) {
                if (frequency.accepts(medianDays)) {
                    return frequency;
                }
            }
            return null;
        }
    }

    public record Payment(UUID transactionId, String merchantKey, UUID merchantId, String merchantName, UUID categoryId,
                          BigDecimal amount, LocalDate date) {
    }

    public record Detection(String merchantKey, UUID merchantId, String merchantName, UUID categoryId,
                            Frequency frequency, BigDecimal typicalAmount, LocalDate firstDate, LocalDate lastDate,
                            LocalDate nextExpectedDate, int occurrences, BigDecimal confidence, boolean active,
                            List<UUID> transactionIds) {
    }

    private RecurringDetector() {
    }

    public static List<Detection> detect(List<Payment> payments, LocalDate today) {
        Map<String, List<Payment>> byMerchant = new LinkedHashMap<>();
        for (Payment payment : payments) {
            byMerchant.computeIfAbsent(payment.merchantKey(), key -> new ArrayList<>()).add(payment);
        }
        List<Detection> detections = new ArrayList<>();
        for (List<Payment> group : byMerchant.values()) {
            Detection detection = analyze(group, today);
            if (detection != null) {
                detections.add(detection);
            }
        }
        detections.sort(Comparator.comparing(Detection::confidence).reversed());
        return detections;
    }

    static Detection analyze(List<Payment> group, LocalDate today) {
        List<Payment> sorted = new ArrayList<>(group);
        sorted.sort(Comparator.comparing(Payment::date));
        // Several payments on one day (e.g. split orders) count once for timing purposes.
        List<Payment> byDay = new ArrayList<>();
        for (Payment payment : sorted) {
            if (byDay.isEmpty() || !byDay.get(byDay.size() - 1).date().equals(payment.date())) {
                byDay.add(payment);
            }
        }
        if (byDay.size() < 2) {
            return null;
        }
        List<Long> gaps = new ArrayList<>();
        for (int i = 1; i < byDay.size(); i++) {
            gaps.add(ChronoUnit.DAYS.between(byDay.get(i - 1).date(), byDay.get(i).date()));
        }
        long medianGap = median(gaps);
        Frequency frequency = Frequency.classify(medianGap);
        if (frequency == null || byDay.size() < frequency.minOccurrences) {
            return null;
        }

        BigDecimal regularity = BigDecimal.valueOf(gaps.stream().filter(frequency::accepts).count())
                .divide(BigDecimal.valueOf(gaps.size()), MC);
        List<BigDecimal> amounts = byDay.stream().map(Payment::amount).toList();
        BigDecimal amountConsistency = clamp(BigDecimal.ONE.subtract(
                coefficientOfVariation(amounts).subtract(new BigDecimal("0.05")).divide(new BigDecimal("0.25"), MC)));
        BigDecimal history = clamp(BigDecimal.valueOf(byDay.size()).divide(BigDecimal.valueOf(6), MC));
        BigDecimal confidence = regularity.multiply(new BigDecimal("0.5"))
                .add(amountConsistency.multiply(new BigDecimal("0.3")))
                .add(history.multiply(new BigDecimal("0.2")))
                .setScale(2, RoundingMode.HALF_UP);
        if (confidence.compareTo(MIN_CONFIDENCE) < 0) {
            return null;
        }

        Payment last = byDay.get(byDay.size() - 1);
        LocalDate next = frequency.next(last.date());
        boolean active = !today.isAfter(next.plusDays(frequency.graceDays()));
        return new Detection(last.merchantKey(), last.merchantId(), last.merchantName(), last.categoryId(), frequency,
                medianAmount(amounts), byDay.get(0).date(), last.date(), next, byDay.size(), confidence, active,
                sorted.stream().map(Payment::transactionId).toList());
    }

    private static long median(List<Long> values) {
        List<Long> sorted = values.stream().sorted().toList();
        int middle = sorted.size() / 2;
        return sorted.size() % 2 == 1 ? sorted.get(middle) : Math.round((sorted.get(middle - 1) + sorted.get(middle)) / 2.0);
    }

    private static BigDecimal medianAmount(List<BigDecimal> values) {
        List<BigDecimal> sorted = values.stream().sorted().toList();
        int middle = sorted.size() / 2;
        BigDecimal median = sorted.size() % 2 == 1 ? sorted.get(middle)
                : sorted.get(middle - 1).add(sorted.get(middle)).divide(BigDecimal.valueOf(2), MC);
        return median.setScale(2, RoundingMode.HALF_UP);
    }

    static BigDecimal coefficientOfVariation(List<BigDecimal> values) {
        BigDecimal mean = values.stream().reduce(BigDecimal.ZERO, BigDecimal::add)
                .divide(BigDecimal.valueOf(values.size()), MC);
        if (mean.signum() == 0) {
            return BigDecimal.ONE;
        }
        BigDecimal variance = values.stream().map(v -> v.subtract(mean).pow(2)).reduce(BigDecimal.ZERO, BigDecimal::add)
                .divide(BigDecimal.valueOf(values.size()), MC);
        return variance.sqrt(MC).divide(mean, MC);
    }

    private static BigDecimal clamp(BigDecimal value) {
        return value.max(BigDecimal.ZERO).min(BigDecimal.ONE);
    }
}

package com.spendos.insights.detectors;

import com.spendos.insights.domain.Insight;
import com.spendos.insights.detectors.SpendingContext.Tx;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Unusual spending compared with the user's own history (never with other people):
 * <ul>
 *   <li><b>Category anomaly</b>: this month's category spend exceeds mean + k standard deviations of the
 *       baseline months and is at least p% and ₹500 above the mean. The "normal range" shown to the
 *       user is mean ± one standard deviation.</li>
 *   <li><b>Large purchase</b>: a single purchase of at least ₹1,000 that is r times the median purchase
 *       in its category (needs 5+ earlier purchases for a meaningful median).</li>
 * </ul>
 * Sensitivity controls k, p and r. At least three months of history are required.
 */
public class AnomalyDetector implements InsightDetector {

    public enum Sensitivity {
        LOW(new BigDecimal("3.0"), new BigDecimal("1.00"), new BigDecimal("5.0")),
        MEDIUM(new BigDecimal("2.0"), new BigDecimal("0.50"), new BigDecimal("3.0")),
        HIGH(new BigDecimal("1.5"), new BigDecimal("0.25"), new BigDecimal("2.5"));

        final BigDecimal deviations;
        final BigDecimal minIncrease;
        final BigDecimal purchaseRatio;

        Sensitivity(BigDecimal deviations, BigDecimal minIncrease, BigDecimal purchaseRatio) {
            this.deviations = deviations;
            this.minIncrease = minIncrease;
            this.purchaseRatio = purchaseRatio;
        }
    }

    static final int MIN_HISTORY_MONTHS = 3;
    static final BigDecimal MIN_ABSOLUTE = BigDecimal.valueOf(500);
    static final BigDecimal MIN_PURCHASE = BigDecimal.valueOf(1000);
    static final int MIN_PURCHASE_HISTORY = 5;

    private final Sensitivity sensitivity;

    public AnomalyDetector(Sensitivity sensitivity) {
        this.sensitivity = sensitivity;
    }

    @Override
    public List<InsightCandidate> detect(SpendingContext context) {
        if (context.baselineMonths().size() < MIN_HISTORY_MONTHS) {
            return List.of();
        }
        List<InsightCandidate> results = new ArrayList<>(categoryAnomalies(context));
        results.addAll(largePurchases(context));
        return results;
    }

    List<InsightCandidate> categoryAnomalies(SpendingContext context) {
        List<InsightCandidate> results = new ArrayList<>();
        Map<UUID, List<BigDecimal>> baseline = context.baselineByMonth(Tx::categoryId);
        String currency = context.currencyCode();
        context.currentBy(Tx::categoryId).forEach((categoryId, transactions) -> {
            List<BigDecimal> history = baseline.getOrDefault(categoryId, List.of());
            if (history.isEmpty()) {
                return;
            }
            BigDecimal current = SpendingContext.sum(transactions);
            BigDecimal mean = SpendingContext.mean(history);
            BigDecimal sd = SpendingContext.standardDeviation(history);
            BigDecimal threshold = mean.add(sd.multiply(sensitivity.deviations));
            boolean anomalous = current.compareTo(threshold) > 0
                    && current.compareTo(mean.multiply(BigDecimal.ONE.add(sensitivity.minIncrease))) > 0
                    && current.subtract(mean).compareTo(MIN_ABSOLUTE) >= 0;
            if (!anomalous) {
                return;
            }
            BigDecimal low = mean.subtract(sd).max(BigDecimal.ZERO).setScale(0, RoundingMode.HALF_UP);
            BigDecimal high = mean.add(sd).setScale(0, RoundingMode.HALF_UP);
            BigDecimal aboveNormal = high.signum() == 0 ? null
                    : current.subtract(high).multiply(BigDecimal.valueOf(100)).divide(high, 0, RoundingMode.HALF_UP);
            String category = transactions.get(0).categoryName();
            String description = "You normally spend " + Texts.money(low, currency) + "–" + Texts.money(high, currency)
                    + " on " + category + " in a month. This month you've spent " + Texts.money(current, currency) + ".";

            Map<String, Object> details = new LinkedHashMap<>();
            details.put("kind", "category");
            details.put("categoryName", category);
            details.put("normalRange", Map.of("min", low, "max", high));
            details.put("currentAmount", current.setScale(2, RoundingMode.HALF_UP));
            details.put("baselineMean", mean.setScale(2, RoundingMode.HALF_UP));
            details.put("baselineMonths", history.size());
            details.put("percentageAboveNormal", aboveNormal);
            details.put("sensitivity", sensitivity.name().toLowerCase());

            results.add(new InsightCandidate(Insight.ANOMALY, "Unusual " + category + " spending", description,
                    current.subtract(mean).setScale(2, RoundingMode.HALF_UP), aboveNormal, categoryId, null, true,
                    "Review your " + category + " transactions for anything unexpected.",
                    new BigDecimal("0.85"), transactions.stream()
                    .sorted(Comparator.comparing(Tx::amount).reversed()).map(Tx::id).toList(), details));
        });
        return results;
    }

    List<InsightCandidate> largePurchases(SpendingContext context) {
        List<InsightCandidate> results = new ArrayList<>();
        Map<UUID, List<BigDecimal>> pastAmounts = new LinkedHashMap<>();
        context.history().forEach(tx -> {
            if (tx.categoryId() != null) {
                pastAmounts.computeIfAbsent(tx.categoryId(), k -> new ArrayList<>()).add(tx.amount());
            }
        });
        String currency = context.currencyCode();
        for (Tx tx : context.current()) {
            List<BigDecimal> past = pastAmounts.get(tx.categoryId());
            if (tx.amount().compareTo(MIN_PURCHASE) < 0 || past == null || past.size() < MIN_PURCHASE_HISTORY) {
                continue;
            }
            BigDecimal median = SpendingContext.median(past);
            if (median.signum() == 0) {
                continue;
            }
            BigDecimal ratio = tx.amount().divide(median, SpendingContext.MC);
            if (ratio.compareTo(sensitivity.purchaseRatio) < 0) {
                continue;
            }
            String where = tx.merchantName() == null ? "" : " at " + tx.merchantName();
            String description = "You spent " + Texts.money(tx.amount(), currency) + where + " on " + tx.categoryName()
                    + ", " + Texts.ratio(ratio) + " your usual " + tx.categoryName() + " purchase of "
                    + Texts.money(median, currency) + ".";

            Map<String, Object> details = new LinkedHashMap<>();
            details.put("kind", "purchase");
            details.put("categoryName", tx.categoryName());
            details.put("merchantName", tx.merchantName());
            details.put("typicalPurchase", median.setScale(2, RoundingMode.HALF_UP));
            details.put("ratio", ratio.setScale(1, RoundingMode.HALF_UP));
            details.put("date", tx.date().toString());

            results.add(new InsightCandidate(Insight.ANOMALY, "Unusually large " + tx.categoryName() + " purchase",
                    description, tx.amount().setScale(2, RoundingMode.HALF_UP),
                    ratio.subtract(BigDecimal.ONE).multiply(BigDecimal.valueOf(100)).setScale(0, RoundingMode.HALF_UP),
                    tx.categoryId(), tx.merchantId(), false,
                    "If you don't recognise this payment, check it with your bank.",
                    new BigDecimal("0.80"), List.of(tx.id()), details));
        }
        return results;
    }
}

package com.spendos.insights.detectors;

import com.spendos.insights.domain.Insight;
import com.spendos.insights.detectors.SpendingContext.Tx;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Money leaks: many small purchases at one merchant that add up. A group is reported only when it is
 * meaningful against the user's own baseline:
 * <ul>
 *   <li>at least {@link #MIN_COUNT} purchases of at most the small-purchase limit this month, and</li>
 *   <li>their total is at least 20% and {@link #MIN_INCREASE} above the three-month average for the same
 *       merchant (pro-rated to today for the current month), or the pattern is new and frequent.</li>
 * </ul>
 */
public class MoneyLeakDetector implements InsightDetector {

    static final int MIN_COUNT = 5;
    static final int MIN_COUNT_WHEN_NEW = 8;
    static final BigDecimal MIN_INCREASE = BigDecimal.valueOf(200);
    static final BigDecimal INCREASE_RATIO = new BigDecimal("1.20");

    private final BigDecimal smallPurchaseLimit;

    public MoneyLeakDetector(BigDecimal smallPurchaseLimit) {
        this.smallPurchaseLimit = smallPurchaseLimit;
    }

    @Override
    public List<InsightCandidate> detect(SpendingContext context) {
        List<InsightCandidate> results = new ArrayList<>();
        Map<UUID, List<BigDecimal>> baseline = context.baselineByMonth(
                tx -> isSmall(tx) && tx.merchantId() != null ? tx.merchantId() : null);
        int baselineMonths = Math.min(3, context.baselineMonths().size());

        context.currentBy(tx -> isSmall(tx) ? tx.merchantId() : null).forEach((merchantId, purchases) -> {
            if (purchases.size() < MIN_COUNT) {
                return;
            }
            BigDecimal total = SpendingContext.sum(purchases);
            List<BigDecimal> history = baseline.getOrDefault(merchantId, List.of());
            List<BigDecimal> recent = history.subList(Math.max(0, history.size() - 3), history.size());
            BigDecimal average = baselineMonths == 0 ? BigDecimal.ZERO
                    : recent.stream().reduce(BigDecimal.ZERO, BigDecimal::add)
                    .divide(BigDecimal.valueOf(baselineMonths), SpendingContext.MC);
            BigDecimal expectedSoFar = average.multiply(context.progress(), SpendingContext.MC);
            boolean isNew = average.signum() == 0;
            boolean significant = isNew
                    ? purchases.size() >= MIN_COUNT_WHEN_NEW && baselineMonths > 0
                    : total.compareTo(expectedSoFar.multiply(INCREASE_RATIO)) >= 0
                      && total.subtract(expectedSoFar).compareTo(MIN_INCREASE) >= 0;
            if (!significant) {
                return;
            }

            Tx sample = purchases.get(0);
            String currency = context.currencyCode();
            String merchant = sample.merchantName();
            BigDecimal increase = isNew ? null : total.subtract(expectedSoFar).multiply(BigDecimal.valueOf(100))
                    .divide(expectedSoFar, 1, RoundingMode.HALF_UP);
            String comparison = isNew ? "a new pattern compared with the previous three months"
                    : Texts.percent(increase) + " more than your three-month average"
                    + (context.inProgress() ? " for this point in the month" : "");
            String description = "You spent " + Texts.money(total, currency) + " across " + purchases.size()
                    + " small purchases at " + merchant + " this month, " + comparison + ".";
            BigDecimal halfSaving = total.divide(BigDecimal.valueOf(2), 0, RoundingMode.HALF_UP);

            Map<String, Object> details = new LinkedHashMap<>();
            details.put("count", purchases.size());
            details.put("total", total.setScale(2, RoundingMode.HALF_UP));
            details.put("threeMonthAverage", average.setScale(2, RoundingMode.HALF_UP));
            details.put("expectedSoFar", expectedSoFar.setScale(2, RoundingMode.HALF_UP));
            details.put("smallPurchaseLimit", smallPurchaseLimit);
            details.put("categoryName", sample.categoryName());
            details.put("merchantName", merchant);

            results.add(new InsightCandidate(Insight.MONEY_LEAK, "Frequent small purchases at " + merchant, description,
                    total.setScale(2, RoundingMode.HALF_UP), increase, sample.categoryId(), merchantId, true,
                    "Halving these purchases would save about " + Texts.money(halfSaving, currency) + " a month.",
                    confidence(baselineMonths), purchases.stream().map(Tx::id).toList(), details));
        });
        return results;
    }

    private boolean isSmall(Tx tx) {
        return tx.amount().compareTo(smallPurchaseLimit) <= 0;
    }

    static BigDecimal confidence(int baselineMonths) {
        return switch (baselineMonths) {
            case 0 -> new BigDecimal("0.50");
            case 1 -> new BigDecimal("0.60");
            case 2 -> new BigDecimal("0.75");
            default -> new BigDecimal("0.90");
        };
    }
}

package com.spendos.insights.detectors;

import com.spendos.insights.domain.Insight;
import com.spendos.insights.detectors.SpendingContext.Tx;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Savings opportunities with a concrete, computed amount:
 * <ul>
 *   <li>the discretionary category that grew the most above its three-month average (at least 15% and ₹500),
 *       with the saving from returning to that average;</li>
 *   <li>the total cost of recurring subscriptions when the user has two or more.</li>
 * </ul>
 */
public class OpportunityDetector implements InsightDetector {

    static final Set<String> DISCRETIONARY = Set.of("Food", "Shopping", "Entertainment", "Travel", "Subscriptions");
    static final BigDecimal MIN_GROWTH = new BigDecimal("0.15");
    static final BigDecimal MIN_SAVING = BigDecimal.valueOf(500);

    @Override
    public List<InsightCandidate> detect(SpendingContext context) {
        List<InsightCandidate> results = new ArrayList<>();
        topGrowingCategory(context).ifPresent(results::add);
        if (context.recurringCount() >= 2 && context.recurringMonthlyCost().signum() > 0) {
            BigDecimal monthly = context.recurringMonthlyCost();
            String currency = context.currencyCode();
            results.add(new InsightCandidate(Insight.OPPORTUNITY, "Review your subscriptions",
                    "You pay about " + Texts.money(monthly, currency) + " a month across " + context.recurringCount()
                            + " recurring payments (" + Texts.money(monthly.multiply(BigDecimal.valueOf(12)), currency)
                            + " a year).",
                    monthly.setScale(2, RoundingMode.HALF_UP), null, null, null, true,
                    "Cancel any subscription you no longer use; each one you drop saves money every month.",
                    new BigDecimal("0.90"), List.of(),
                    Map.of("recurringCount", context.recurringCount(), "annualCost",
                            monthly.multiply(BigDecimal.valueOf(12)).setScale(2, RoundingMode.HALF_UP))));
        }
        return results;
    }

    private java.util.Optional<InsightCandidate> topGrowingCategory(SpendingContext context) {
        if (context.baselineMonths().isEmpty()) {
            return java.util.Optional.empty();
        }
        Map<UUID, List<BigDecimal>> baseline = context.baselineByMonth(Tx::categoryId);
        InsightCandidate best = null;
        BigDecimal bestSaving = BigDecimal.ZERO;
        for (Map.Entry<UUID, List<Tx>> entry : context.currentBy(Tx::categoryId).entrySet()) {
            String category = entry.getValue().get(0).categoryName();
            if (!DISCRETIONARY.contains(category)) {
                continue;
            }
            List<BigDecimal> history = baseline.getOrDefault(entry.getKey(), List.of());
            List<BigDecimal> recent = history.subList(Math.max(0, history.size() - 3), history.size());
            BigDecimal average = SpendingContext.mean(recent);
            if (average.signum() == 0) {
                continue;
            }
            BigDecimal expected = average.multiply(context.progress(), SpendingContext.MC);
            BigDecimal current = SpendingContext.sum(entry.getValue());
            BigDecimal saving = current.subtract(expected);
            if (saving.compareTo(MIN_SAVING) < 0 || current.compareTo(expected.multiply(BigDecimal.ONE.add(MIN_GROWTH))) < 0
                    || saving.compareTo(bestSaving) <= 0) {
                continue;
            }
            BigDecimal growth = saving.multiply(BigDecimal.valueOf(100)).divide(expected, 0, RoundingMode.HALF_UP);
            String currency = context.currencyCode();
            best = new InsightCandidate(Insight.OPPORTUNITY, category + " spending is up " + Texts.percent(growth),
                    category + " spending is " + Texts.money(saving, currency) + " (" + Texts.percent(growth)
                            + ") above your three-month average" + (context.inProgress() ? " for this point in the month" : "")
                            + ".",
                    saving.setScale(2, RoundingMode.HALF_UP), growth, entry.getKey(), null, true,
                    "Bringing " + category + " back to your average would save about " + Texts.money(saving, currency)
                            + " this month.",
                    new BigDecimal("0.80"), entry.getValue().stream().map(Tx::id).toList(),
                    Map.of("categoryName", category, "threeMonthAverage", average.setScale(2, RoundingMode.HALF_UP),
                            "currentAmount", current.setScale(2, RoundingMode.HALF_UP)));
            bestSaving = saving;
        }
        return java.util.Optional.ofNullable(best);
    }
}

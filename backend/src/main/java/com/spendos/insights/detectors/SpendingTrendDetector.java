package com.spendos.insights.detectors;

import com.spendos.insights.domain.Insight;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Overall pace: total spending vs the three-month average (pro-rated for the current month) when it
 * differs by 10% or more, and a low savings rate warning when income is known.
 */
public class SpendingTrendDetector implements InsightDetector {

    static final BigDecimal MIN_CHANGE = BigDecimal.TEN;
    static final BigDecimal LOW_SAVINGS_RATE = new BigDecimal("0.10");

    @Override
    public List<InsightCandidate> detect(SpendingContext context) {
        List<InsightCandidate> results = new ArrayList<>();
        String currency = context.currencyCode();
        BigDecimal current = context.currentExpense();
        List<BigDecimal> totals = context.baselineTotals();
        List<BigDecimal> recent = totals.subList(Math.max(0, totals.size() - 3), totals.size());
        BigDecimal average = SpendingContext.mean(recent);
        if (average.signum() > 0) {
            BigDecimal expected = average.multiply(context.progress(), SpendingContext.MC);
            BigDecimal change = current.subtract(expected).multiply(BigDecimal.valueOf(100))
                    .divide(expected, 1, RoundingMode.HALF_UP);
            if (change.abs().compareTo(MIN_CHANGE) >= 0) {
                boolean higher = change.signum() > 0;
                String pace = context.inProgress() ? " for this point in the month" : "";
                results.add(new InsightCandidate(Insight.SPENDING_TREND,
                        "Spending is " + Texts.percent(change.abs()) + (higher ? " higher" : " lower") + " than usual",
                        "You've spent " + Texts.money(current, currency) + " this month, " + Texts.percent(change.abs())
                                + (higher ? " more" : " less") + " than your three-month average of "
                                + Texts.money(expected, currency) + pace + ".",
                        current.subtract(expected).abs().setScale(2, RoundingMode.HALF_UP), change, null, null, higher,
                        higher ? "Check the categories driving the increase on your analytics page." : null,
                        new BigDecimal("0.85"), List.of(),
                        Map.of("currentAmount", current.setScale(2, RoundingMode.HALF_UP),
                                "expectedAmount", expected.setScale(2, RoundingMode.HALF_UP))));
            }
        }
        BigDecimal income = context.currentIncome();
        if (!context.inProgress() && income.signum() > 0) {
            BigDecimal savingsRate = income.subtract(current).divide(income, SpendingContext.MC);
            if (savingsRate.compareTo(LOW_SAVINGS_RATE) < 0) {
                BigDecimal percent = savingsRate.multiply(BigDecimal.valueOf(100));
                results.add(new InsightCandidate(Insight.SPENDING_TREND, "Low savings this month",
                        savingsRate.signum() < 0
                                ? "You spent " + Texts.money(current.subtract(income), currency) + " more than you earned this month."
                                : "You saved only " + Texts.percent(percent) + " of your income this month.",
                        income.subtract(current).setScale(2, RoundingMode.HALF_UP), percent.setScale(1, RoundingMode.HALF_UP),
                        null, null, true, "Aim to save at least 10-20% of income; set a budget for your largest category.",
                        new BigDecimal("0.90"), List.of(), Map.of("savingsRate", savingsRate.setScale(3, RoundingMode.HALF_UP))));
            }
        }
        return results;
    }
}

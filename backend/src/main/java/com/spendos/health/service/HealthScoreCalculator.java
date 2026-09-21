package com.spendos.health.service;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

/**
 * Explainable 0-100 financial health score (DEVELOPMENT_PLAN.md Phase 14 weights):
 * savings rate 30, budget adherence 25, spending volatility 20, recurring burden 15, emergency buffer 10.
 * Each factor earns a share of its weight from a transparent linear rule. Factors without data are
 * reported as "not scored" and the score is normalized over the factors that could be scored, instead
 * of inventing a value. Pure function: no I/O, fully unit-testable.
 */
public final class HealthScoreCalculator {

    public static final int W_SAVINGS = 30;
    public static final int W_BUDGET = 25;
    public static final int W_VOLATILITY = 20;
    public static final int W_RECURRING = 15;
    public static final int W_BUFFER = 10;

    /** Raw inputs; nullable fields mean "no data". Money values are monthly averages. */
    public record Inputs(
            BigDecimal averageMonthlyIncome,
            BigDecimal averageMonthlyExpense,
            List<BigDecimal> monthlyExpenses,
            BigDecimal budgetAdherenceRatio,
            int budgetsTracked,
            BigDecimal averageMonthlyRecurring,
            BigDecimal liquidBalance) {
    }

    public record Factor(String key, String label, int weight, BigDecimal points, BigDecimal metric,
                         boolean scored, String explanation) {
    }

    public record Result(Integer score, List<Factor> factors, BigDecimal savingsRate, BigDecimal volatility,
                         BigDecimal recurringBurden, BigDecimal bufferMonths, String summary) {
    }

    private static final MathContext MC = new MathContext(10, RoundingMode.HALF_UP);

    private HealthScoreCalculator() {
    }

    public static Result calculate(Inputs in) {
        BigDecimal income = nz(in.averageMonthlyIncome());
        BigDecimal expense = nz(in.averageMonthlyExpense());
        if (income.signum() == 0 && expense.signum() == 0) {
            return new Result(null, List.of(), null, null, null, null,
                    "Not enough data yet: import at least one month of transactions to get a health score.");
        }
        List<Factor> factors = new ArrayList<>();

        // Savings rate: 30%+ of income saved earns full points; spending more than income earns none.
        BigDecimal savingsRate = null;
        if (income.signum() > 0) {
            savingsRate = income.subtract(expense).divide(income, MC);
            BigDecimal share = clamp(savingsRate.divide(new BigDecimal("0.30"), MC));
            factors.add(factor("savingsRate", "Savings rate", W_SAVINGS, share, pct(savingsRate), true,
                    "You saved " + pct(savingsRate).setScale(0, RoundingMode.HALF_UP) + "% of income; 30% or more earns full points."));
        } else {
            factors.add(factor("savingsRate", "Savings rate", W_SAVINGS, BigDecimal.ZERO, null, false,
                    "No income recorded, so savings rate cannot be measured."));
        }

        // Budget adherence: share of tracked budgets kept within their limit.
        if (in.budgetsTracked() > 0 && in.budgetAdherenceRatio() != null) {
            BigDecimal ratio = clamp(in.budgetAdherenceRatio());
            factors.add(factor("budgetAdherence", "Budget adherence", W_BUDGET, ratio, pct(ratio), true,
                    pct(ratio).setScale(0, RoundingMode.HALF_UP) + "% of your budget limits were respected."));
        } else {
            factors.add(factor("budgetAdherence", "Budget adherence", W_BUDGET, BigDecimal.ZERO, null, false,
                    "No budgets set, so this factor is not scored. Create a budget to include it."));
        }

        // Volatility: coefficient of variation of monthly spending; 10% or less is steady (full points), 50%+ none.
        BigDecimal volatility = coefficientOfVariation(in.monthlyExpenses());
        if (volatility != null) {
            BigDecimal share = clamp(BigDecimal.ONE.subtract(
                    volatility.subtract(new BigDecimal("0.10")).divide(new BigDecimal("0.40"), MC)));
            factors.add(factor("spendingVolatility", "Spending consistency", W_VOLATILITY, share, pct(volatility), true,
                    "Monthly spending varies by " + pct(volatility).setScale(0, RoundingMode.HALF_UP)
                            + "% around its average; under 10% earns full points."));
        } else {
            factors.add(factor("spendingVolatility", "Spending consistency", W_VOLATILITY, BigDecimal.ZERO, null, false,
                    "Needs at least 3 months of spending history."));
        }

        // Recurring burden: recurring payments as a share of income; 10% or less full points, 50%+ none.
        BigDecimal recurringBurden = null;
        if (income.signum() > 0) {
            recurringBurden = nz(in.averageMonthlyRecurring()).divide(income, MC);
            BigDecimal share = clamp(BigDecimal.ONE.subtract(
                    recurringBurden.subtract(new BigDecimal("0.10")).divide(new BigDecimal("0.40"), MC)));
            factors.add(factor("recurringBurden", "Recurring payments", W_RECURRING, share, pct(recurringBurden), true,
                    "Recurring payments take " + pct(recurringBurden).setScale(0, RoundingMode.HALF_UP)
                            + "% of income; 10% or less earns full points."));
        } else {
            factors.add(factor("recurringBurden", "Recurring payments", W_RECURRING, BigDecimal.ZERO, null, false,
                    "No income recorded, so recurring burden cannot be measured."));
        }

        // Emergency buffer: months of expenses covered by balances; 6+ months full points.
        BigDecimal bufferMonths = null;
        if (in.liquidBalance() != null && in.liquidBalance().signum() > 0 && expense.signum() > 0) {
            bufferMonths = in.liquidBalance().divide(expense, MC);
            BigDecimal share = clamp(bufferMonths.divide(BigDecimal.valueOf(6), MC));
            factors.add(factor("emergencyBuffer", "Emergency buffer", W_BUFFER, share, bufferMonths, true,
                    "Your balances cover about " + bufferMonths.setScale(1, RoundingMode.HALF_UP)
                            + " months of spending; 6 months earns full points."));
        } else {
            factors.add(factor("emergencyBuffer", "Emergency buffer", W_BUFFER, BigDecimal.ZERO, null, false,
                    "Add opening balances to your accounts to include your emergency buffer."));
        }

        int availableWeight = factors.stream().filter(Factor::scored).mapToInt(Factor::weight).sum();
        BigDecimal earned = factors.stream().filter(Factor::scored).map(Factor::points)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        Integer score = availableWeight == 0 ? null
                : earned.multiply(BigDecimal.valueOf(100)).divide(BigDecimal.valueOf(availableWeight), 0, RoundingMode.HALF_UP)
                .intValue();
        String summary = score == null ? "Not enough data for a score."
                : score >= 80 ? "Excellent: your finances are in strong shape."
                : score >= 60 ? "Good, with room to improve."
                : score >= 40 ? "Fair: a few habits are holding your score back."
                : "Needs attention: focus on the lowest-scoring factors first.";
        return new Result(score, factors, savingsRate, volatility, recurringBurden, bufferMonths, summary);
    }

    /** Standard deviation / mean over months; null with fewer than 3 months or zero mean. */
    static BigDecimal coefficientOfVariation(List<BigDecimal> values) {
        if (values == null || values.size() < 3) {
            return null;
        }
        BigDecimal mean = values.stream().reduce(BigDecimal.ZERO, BigDecimal::add)
                .divide(BigDecimal.valueOf(values.size()), MC);
        if (mean.signum() == 0) {
            return null;
        }
        BigDecimal variance = values.stream()
                .map(v -> v.subtract(mean).pow(2))
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .divide(BigDecimal.valueOf(values.size()), MC);
        return variance.sqrt(MC).divide(mean, MC);
    }

    private static Factor factor(String key, String label, int weight, BigDecimal share, BigDecimal metric,
                                 boolean scored, String explanation) {
        BigDecimal points = share.multiply(BigDecimal.valueOf(weight)).setScale(1, RoundingMode.HALF_UP);
        return new Factor(key, label, weight, points, metric == null ? null : metric.setScale(2, RoundingMode.HALF_UP),
                scored, explanation);
    }

    private static BigDecimal clamp(BigDecimal value) {
        if (value.compareTo(BigDecimal.ZERO) < 0) {
            return BigDecimal.ZERO;
        }
        return value.compareTo(BigDecimal.ONE) > 0 ? BigDecimal.ONE : value;
    }

    private static BigDecimal pct(BigDecimal ratio) {
        return ratio.multiply(BigDecimal.valueOf(100));
    }

    private static BigDecimal nz(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }
}

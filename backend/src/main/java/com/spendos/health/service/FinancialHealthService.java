package com.spendos.health.service;

import com.spendos.analytics.repository.AggregateQueries;
import com.spendos.analytics.repository.AggregateQueries.MonthTotals;
import com.spendos.health.service.HealthScoreCalculator.Inputs;
import com.spendos.health.service.HealthScoreCalculator.Result;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.UUID;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Gathers the user's data for the health score as of a month: the three months ending with it. */
@Service
public class FinancialHealthService {

    static final int WINDOW_MONTHS = 3;
    static final int VOLATILITY_MONTHS = 6;

    private final AggregateQueries aggregates;
    private final ObjectProvider<BudgetAdherenceProvider> budgetAdherence;

    public FinancialHealthService(AggregateQueries aggregates, ObjectProvider<BudgetAdherenceProvider> budgetAdherence) {
        this.aggregates = aggregates;
        this.budgetAdherence = budgetAdherence;
    }

    /** The score together with the inputs it was computed from (used for explanations and advice). */
    public record Snapshot(YearMonth month, Result result, Inputs inputs) {
    }

    @Transactional(readOnly = true)
    public Result calculate(UUID userId, YearMonth asOf) {
        return snapshot(userId, asOf).result();
    }

    @Transactional(readOnly = true)
    public Snapshot snapshot(UUID userId, YearMonth asOf) {
        YearMonth windowStart = asOf.minusMonths(WINDOW_MONTHS - 1L);
        List<MonthTotals> window = activeMonths(aggregates.monthlyTotals(userId, windowStart, asOf));
        List<MonthTotals> history = activeMonths(
                aggregates.monthlyTotals(userId, asOf.minusMonths(VOLATILITY_MONTHS - 1L), asOf));

        int months = Math.max(1, window.size());
        BigDecimal income = window.stream().map(MonthTotals::income).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal expense = window.stream().map(MonthTotals::expense).reduce(BigDecimal.ZERO, BigDecimal::add);
        LocalDate start = windowStart.atDay(1);
        LocalDate end = asOf.atEndOfMonth();
        BigDecimal recurring = aggregates.recurringSpend(userId, start, end);

        BudgetAdherenceProvider.Adherence adherence = budgetAdherence.getIfAvailable() == null
                ? BudgetAdherenceProvider.Adherence.none()
                : budgetAdherence.getObject().adherence(userId, start, end);

        Inputs inputs = new Inputs(
                average(income, months), average(expense, months),
                history.stream().map(MonthTotals::expense).toList(),
                adherence.ratio(), adherence.tracked(),
                average(recurring, months),
                aggregates.estimatedBalance(userId));
        return new Snapshot(asOf, HealthScoreCalculator.calculate(inputs), inputs);
    }

    /** Drops leading months before the user's data begins so new users are not penalized for them. */
    private static List<MonthTotals> activeMonths(List<MonthTotals> months) {
        int first = 0;
        while (first < months.size() && months.get(first).income().signum() == 0
                && months.get(first).expense().signum() == 0) {
            first++;
        }
        return months.subList(first, months.size());
    }

    private static BigDecimal average(BigDecimal total, int months) {
        return total.divide(BigDecimal.valueOf(months), 2, RoundingMode.HALF_UP);
    }
}

package com.spendos.simulations.service;

import com.spendos.goals.service.GoalService;
import com.spendos.simulations.dto.SimulationDtos.GoalImpact;
import com.spendos.simulations.dto.SimulationDtos.Impact;
import com.spendos.simulations.dto.SimulationDtos.Results;
import com.spendos.simulations.dto.SimulationDtos.Scenario;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Pure what-if arithmetic (no LLM, no I/O). Works from the user's monthly baseline: average income,
 * average spending and average spending per category. A category reduction can never cut more than
 * that category's average. One-time purchases affect the annual figures and delay goals by the
 * purchase amount.
 */
public final class SimulationCalculator {

    private static final BigDecimal TWELVE = BigDecimal.valueOf(12);

    public record Baseline(BigDecimal monthlyIncome, BigDecimal monthlyExpense, Map<UUID, BigDecimal> categoryMonthly,
                           Map<UUID, String> categoryNames, int months) {
    }

    public record GoalState(UUID id, String name, BigDecimal remaining) {
    }

    private SimulationCalculator() {
    }

    public static Results calculate(Baseline baseline, List<Scenario> scenarios, List<GoalState> goals) {
        BigDecimal income = baseline.monthlyIncome();
        BigDecimal expense = baseline.monthlyExpense();
        BigDecimal oneTime = BigDecimal.ZERO;
        List<String> notes = new ArrayList<>();

        for (Scenario scenario : scenarios) {
            BigDecimal amount = scenario.amount();
            boolean oneOff = "one_time".equals(scenario.period()) || "one_time_purchase".equals(scenario.type());
            switch (scenario.type()) {
                case "spend_reduction" -> {
                    BigDecimal cut = amount.abs();
                    if (scenario.categoryId() != null) {
                        BigDecimal categoryAverage = baseline.categoryMonthly().getOrDefault(scenario.categoryId(), BigDecimal.ZERO);
                        if (cut.compareTo(categoryAverage) > 0) {
                            notes.add("You spend about " + categoryAverage.setScale(0, RoundingMode.HALF_UP) + " a month on "
                                    + baseline.categoryNames().getOrDefault(scenario.categoryId(), "that category")
                                    + ", so the reduction was capped at that amount.");
                            cut = categoryAverage;
                        }
                    }
                    if (oneOff) {
                        oneTime = oneTime.subtract(cut);
                    } else {
                        expense = expense.subtract(cut).max(BigDecimal.ZERO);
                    }
                }
                case "spend_increase" -> {
                    if (oneOff) {
                        oneTime = oneTime.add(amount.abs());
                    } else {
                        expense = expense.add(amount.abs());
                    }
                }
                case "income_change" -> income = income.add(amount).max(BigDecimal.ZERO);
                case "savings_increase" -> expense = expense.subtract(amount.abs()).max(BigDecimal.ZERO);
                case "one_time_purchase" -> oneTime = oneTime.add(amount.abs());
                default -> throw new IllegalArgumentException("Unknown scenario type " + scenario.type());
            }
        }

        BigDecimal savingsBefore = baseline.monthlyIncome().subtract(baseline.monthlyExpense());
        BigDecimal savingsAfter = income.subtract(expense);
        Impact monthly = impact(baseline.monthlyExpense(), expense);
        Impact annual = impact(baseline.monthlyExpense().multiply(TWELVE), expense.multiply(TWELVE).add(oneTime));
        Impact monthlySavings = impact(savingsBefore, savingsAfter);
        Impact annualSavings = impact(savingsBefore.multiply(TWELVE), savingsAfter.multiply(TWELVE).subtract(oneTime));

        BigDecimal oneTimeSpend = oneTime.max(BigDecimal.ZERO);
        List<GoalImpact> goalImpact = goals.stream().map(goal -> new GoalImpact(goal.id(), goal.name(),
                        GoalService.monthsNeeded(goal.remaining(), savingsBefore),
                        GoalService.monthsNeeded(goal.remaining().add(oneTimeSpend), savingsAfter)))
                .toList();
        if (savingsAfter.signum() <= 0) {
            notes.add("With these changes you would not be saving anything each month.");
        }
        if (baseline.months() == 0) {
            notes.add("No complete months of history yet, so the baseline is zero; import a statement first.");
        }
        return new Results(monthly, annual, monthlySavings, annualSavings, goalImpact, notes, baseline.months());
    }

    static Impact impact(BigDecimal before, BigDecimal after) {
        BigDecimal change = after.subtract(before);
        BigDecimal percentage = before.signum() == 0 ? null
                : change.multiply(BigDecimal.valueOf(100)).divide(before.abs(), 1, RoundingMode.HALF_UP);
        return new Impact(before.setScale(2, RoundingMode.HALF_UP), after.setScale(2, RoundingMode.HALF_UP),
                change.setScale(2, RoundingMode.HALF_UP), percentage);
    }
}

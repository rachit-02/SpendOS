package com.spendos.health.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.spendos.health.dto.HealthMetricsDtos.FactorChange;
import com.spendos.health.dto.HealthMetricsDtos.Recommendation;
import com.spendos.health.service.FinancialHealthService.Snapshot;
import com.spendos.health.service.HealthAdvisor.ChangeSummary;
import com.spendos.health.service.HealthAdvisor.TopCategory;
import com.spendos.health.service.HealthScoreCalculator.Inputs;
import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.List;
import org.junit.jupiter.api.Test;

class HealthAdvisorTest {

    private static final List<BigDecimal> STEADY = List.of(new BigDecimal("50000"), new BigDecimal("52500"),
            new BigDecimal("55000"));
    private static final TopCategory FOOD = new TopCategory("Food", new BigDecimal("8400"));

    private static Snapshot snapshot(YearMonth month, String expense, BigDecimal adherence, int budgets, String balance) {
        Inputs inputs = new Inputs(new BigDecimal("75000"), new BigDecimal(expense), STEADY, adherence, budgets,
                new BigDecimal("3000"), balance == null ? null : new BigDecimal(balance));
        return new Snapshot(month, HealthScoreCalculator.calculate(inputs), inputs);
    }

    @Test
    void explainsWhichFactorMovedTheScoreAndWhy() {
        // Savings rate drops from 30% (full 30 points) to 20% (20 points); nothing else changes.
        Snapshot august = snapshot(YearMonth.of(2026, 8), "52500", null, 0, "100000");
        Snapshot september = snapshot(YearMonth.of(2026, 9), "60000", null, 0, "100000");

        ChangeSummary summary = HealthAdvisor.explain(september, august);

        assertThat(september.result().score()).isLessThan(august.result().score());
        assertThat(summary.summary()).isEqualTo("Your score fell from " + august.result().score() + " to "
                + september.result().score() + ", mainly because of savings rate.");
        FactorChange savings = summary.changes().get(0);
        assertThat(savings.factor()).isEqualTo("savingsRate");
        assertThat(savings.change()).isEqualByComparingTo("-10");
        assertThat(savings.reason()).isEqualTo("You saved 20% of your income, compared with 30% before.");
    }

    @Test
    void reportsFactorsThatBecomeMeasurable() {
        Snapshot before = snapshot(YearMonth.of(2026, 8), "52500", null, 0, "100000");
        Snapshot after = snapshot(YearMonth.of(2026, 9), "52500", BigDecimal.ONE, 2, "100000");

        ChangeSummary summary = HealthAdvisor.explain(after, before);

        assertThat(summary.changes()).anySatisfy(change -> {
            assertThat(change.factor()).isEqualTo("budgetAdherence");
            assertThat(change.reason()).isEqualTo("Budget adherence is now included in your score.");
            assertThat(change.previousPoints()).isNull();
        });
    }

    @Test
    void firstScoreAndUnchangedScoreAreSaidPlainly() {
        Snapshot empty = new Snapshot(YearMonth.of(2026, 8), HealthScoreCalculator.calculate(
                new Inputs(BigDecimal.ZERO, BigDecimal.ZERO, List.of(), null, 0, BigDecimal.ZERO, null)),
                new Inputs(BigDecimal.ZERO, BigDecimal.ZERO, List.of(), null, 0, BigDecimal.ZERO, null));
        Snapshot first = snapshot(YearMonth.of(2026, 9), "52500", null, 0, "100000");

        assertThat(HealthAdvisor.explain(first, empty).summary()).startsWith("This is your first month with a score");
        assertThat(HealthAdvisor.explain(first, first).summary()).isEqualTo("Your score stayed at " + first.result().score() + ".");
    }

    @Test
    void recommendsConcreteActionsForTheWeakestFactorsFirst() {
        Snapshot september = snapshot(YearMonth.of(2026, 9), "60000", null, 0, "100000");

        List<Recommendation> advice = HealthAdvisor.recommend(september, FOOD, "INR");

        assertThat(advice).extracting(Recommendation::factor)
                .containsExactly("budgetAdherence", "savingsRate", "emergencyBuffer");
        assertThat(advice.get(0).action()).isEqualTo("Create a monthly budget, starting with Food (about ₹8,400 a month).");
        assertThat(advice.get(0).potentialPoints()).isEqualByComparingTo("25");
        // 60,000 spent vs 70% of 75,000 = 52,500 -> cut 7,500
        assertThat(advice.get(1).action()).startsWith("Spend about ₹7,500 less a month to save 30% of your income")
                .contains("Food is your biggest category");
        // 6 x 60,000 = 360,000 target, 100,000 saved
        assertThat(advice.get(2).action()).contains("₹360,000").contains("₹260,000 to go");
    }

    @Test
    void strongFactorsGetNoAdvice() {
        Snapshot healthy = snapshot(YearMonth.of(2026, 9), "45000", BigDecimal.ONE, 3, "400000");

        assertThat(HealthAdvisor.recommend(healthy, FOOD, "INR")).extracting(Recommendation::factor)
                .doesNotContain("savingsRate", "budgetAdherence", "emergencyBuffer", "recurringBurden");
    }
}

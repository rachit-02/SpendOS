package com.spendos.simulations;

import static org.assertj.core.api.Assertions.assertThat;

import com.spendos.simulations.dto.SimulationDtos.Results;
import com.spendos.simulations.dto.SimulationDtos.Scenario;
import com.spendos.simulations.service.SimulationCalculator;
import com.spendos.simulations.service.SimulationCalculator.Baseline;
import com.spendos.simulations.service.SimulationCalculator.GoalState;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class SimulationCalculatorTest {

    private static final UUID FOOD = UUID.randomUUID();
    // Income 75,000, spending 42,300 -> saving 32,700 a month.
    private static final Baseline BASELINE = new Baseline(new BigDecimal("75000"), new BigDecimal("42300"),
            Map.of(FOOD, new BigDecimal("8400")), Map.of(FOOD, "Food"), 3);
    private static final GoalState EMERGENCY = new GoalState(UUID.randomUUID(), "Emergency Fund", new BigDecimal("170000"));

    private static Scenario scenario(String type, UUID category, String amount, String period) {
        return new Scenario(type, category, new BigDecimal(amount), period, null);
    }

    @Test
    void matchesTheApiDesignExample() {
        Results results = SimulationCalculator.calculate(BASELINE,
                List.of(scenario("spend_reduction", FOOD, "2000", "monthly")), List.of(EMERGENCY));

        assertThat(results.monthlyImpact().before()).isEqualByComparingTo("42300");
        assertThat(results.monthlyImpact().after()).isEqualByComparingTo("40300");
        assertThat(results.monthlyImpact().change()).isEqualByComparingTo("-2000");
        assertThat(results.monthlyImpact().changePercentage()).isEqualByComparingTo("-4.7");
        assertThat(results.annualImpact().before()).isEqualByComparingTo("507600");
        assertThat(results.annualImpact().after()).isEqualByComparingTo("483600");
        assertThat(results.annualImpact().change()).isEqualByComparingTo("-24000");
        assertThat(results.monthlySavings().after()).isEqualByComparingTo("34700");
        // 170,000 / 32,700 = 5.2 -> 6 months; / 34,700 = 4.9 -> 5 months
        assertThat(results.goalImpact()).singleElement().satisfies(g -> {
            assertThat(g.currentMonthsToCompletion()).isEqualTo(6);
            assertThat(g.projectedMonthsWithSimulation()).isEqualTo(5);
        });
    }

    @Test
    void categoryReductionIsCappedAtWhatIsActuallySpent() {
        Results results = SimulationCalculator.calculate(BASELINE,
                List.of(scenario("spend_reduction", FOOD, "20000", "monthly")), List.of());

        assertThat(results.monthlyImpact().change()).isEqualByComparingTo("-8400");
        assertThat(results.notes()).anySatisfy(note -> assertThat(note).contains("capped"));
    }

    @Test
    void oneTimePurchaseAffectsTheYearAndDelaysGoals() {
        Results results = SimulationCalculator.calculate(BASELINE,
                List.of(scenario("one_time_purchase", null, "70000", "one_time")), List.of(EMERGENCY));

        assertThat(results.monthlyImpact().change()).isEqualByComparingTo("0");
        assertThat(results.annualImpact().after()).isEqualByComparingTo("577600");
        assertThat(results.annualSavings().change()).isEqualByComparingTo("-70000");
        // 240,000 / 32,700 = 7.3 -> 8 months
        assertThat(results.goalImpact().get(0).projectedMonthsWithSimulation()).isEqualTo(8);
    }

    @Test
    void savingsIncreaseAndIncomeChangesCombine() {
        Results results = SimulationCalculator.calculate(BASELINE, List.of(
                scenario("savings_increase", null, "5000", "monthly"),
                scenario("income_change", null, "-80000", "monthly")), List.of(EMERGENCY));

        assertThat(results.monthlySavings().after()).isEqualByComparingTo("-37300");
        assertThat(results.goalImpact().get(0).projectedMonthsWithSimulation()).isNull();
        assertThat(results.notes()).anySatisfy(note -> assertThat(note).contains("not be saving"));
    }
}

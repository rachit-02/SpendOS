package com.spendos.health.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.spendos.health.service.HealthScoreCalculator.Factor;
import com.spendos.health.service.HealthScoreCalculator.Inputs;
import com.spendos.health.service.HealthScoreCalculator.Result;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

class HealthScoreCalculatorTest {

    private static BigDecimal d(String value) {
        return new BigDecimal(value);
    }

    private static Factor factor(Result result, String key) {
        return result.factors().stream().filter(f -> f.key().equals(key)).findFirst().orElseThrow();
    }

    @Test
    void noDataGivesNoScoreInsteadOfAMadeUpNumber() {
        Result result = HealthScoreCalculator.calculate(new Inputs(d("0"), d("0"), List.of(), null, 0, d("0"), null));
        assertThat(result.score()).isNull();
        assertThat(result.summary()).contains("Not enough data");
    }

    @Test
    void excellentHabitsScoreOneHundred() {
        Result result = HealthScoreCalculator.calculate(new Inputs(
                d("100000"), d("60000"), List.of(d("60000"), d("61000"), d("59000")),
                d("1.0"), 3, d("5000"), d("600000")));

        assertThat(result.score()).isEqualTo(100);
        assertThat(result.factors()).allMatch(Factor::scored);
        assertThat(result.factors().stream().map(Factor::points).reduce(BigDecimal.ZERO, BigDecimal::add))
                .isEqualByComparingTo("100");
    }

    @Test
    void eachFactorFollowsItsLinearRule() {
        Result result = HealthScoreCalculator.calculate(new Inputs(
                d("100000"), d("85000"), List.of(d("50000"), d("85000"), d("120000")),
                d("0.5"), 2, d("30000"), d("255000")));

        // savings 15% of a 30% target -> half of 30 points
        assertThat(factor(result, "savingsRate").points()).isEqualByComparingTo("15.0");
        assertThat(factor(result, "savingsRate").metric()).isEqualByComparingTo("15.00");
        // half the budgets respected -> half of 25
        assertThat(factor(result, "budgetAdherence").points()).isEqualByComparingTo("12.5");
        // recurring 30% of income: (0.30-0.10)/0.40 = 0.5 penalty -> 7.5 of 15
        assertThat(factor(result, "recurringBurden").points()).isEqualByComparingTo("7.5");
        // buffer 3 months of 6 -> 5 of 10
        assertThat(factor(result, "emergencyBuffer").points()).isEqualByComparingTo("5.0");
        // volatility: CV = 28577/85000 ~ 0.336 -> (0.336-0.10)/0.40 = 0.59 penalty -> ~8.2 of 20
        assertThat(factor(result, "spendingVolatility").points()).isBetween(d("8.0"), d("8.4"));
    }

    @Test
    void factorsWithoutDataAreExcludedAndScoreIsNormalized() {
        // Only savings (30) and recurring (15) can be scored; both perfect -> 45/45 -> 100
        Result result = HealthScoreCalculator.calculate(new Inputs(
                d("100000"), d("50000"), List.of(d("50000")), null, 0, d("0"), null));

        assertThat(factor(result, "budgetAdherence").scored()).isFalse();
        assertThat(factor(result, "budgetAdherence").explanation()).contains("No budgets set");
        assertThat(factor(result, "spendingVolatility").scored()).isFalse();
        assertThat(factor(result, "emergencyBuffer").scored()).isFalse();
        assertThat(result.score()).isEqualTo(100);
    }

    @Test
    void spendingMoreThanIncomeEarnsNoSavingsPointsAndScoreStaysInRange() {
        Result result = HealthScoreCalculator.calculate(new Inputs(
                d("30000"), d("45000"), List.of(d("10000"), d("45000"), d("90000")), d("0"), 2, d("20000"), d("0")));

        assertThat(factor(result, "savingsRate").points()).isEqualByComparingTo("0");
        assertThat(result.score()).isBetween(0, 100).isLessThan(20);
    }

    @Test
    void coefficientOfVariationNeedsThreeMonths() {
        assertThat(HealthScoreCalculator.coefficientOfVariation(List.of(d("1"), d("2")))).isNull();
        assertThat(HealthScoreCalculator.coefficientOfVariation(List.of(d("100"), d("100"), d("100"))))
                .isEqualByComparingTo("0");
    }
}

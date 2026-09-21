package com.spendos.common.util;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;

class PercentagesTest {

    private final Random random = new Random(42);

    @Test
    void thirdsStillSumToHundred() {
        List<BigDecimal> shares = Percentages.ofTotal(List.of(BigDecimal.ONE, BigDecimal.ONE, BigDecimal.ONE));
        assertThat(shares).containsExactlyInAnyOrder(new BigDecimal("33.4"), new BigDecimal("33.3"), new BigDecimal("33.3"));
    }

    @RepeatedTest(50)
    void randomAmountsAlwaysSumToExactlyHundred() {
        List<BigDecimal> parts = new ArrayList<>();
        int count = 1 + random.nextInt(12);
        for (int i = 0; i < count; i++) {
            parts.add(BigDecimal.valueOf(random.nextInt(100_000_00) + 1, 2));
        }
        BigDecimal sum = Percentages.ofTotal(parts).stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        assertThat(sum).isEqualByComparingTo("100.0");
    }

    @Test
    void zeroTotalGivesZeros() {
        assertThat(Percentages.ofTotal(List.of(BigDecimal.ZERO, BigDecimal.ZERO)))
                .allMatch(p -> p.signum() == 0);
    }
}

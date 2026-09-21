package com.spendos.demo;

import static org.assertj.core.api.Assertions.assertThat;

import com.spendos.demo.service.DemoDataGenerator;
import com.spendos.demo.service.DemoDataGenerator.DemoTransaction;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import org.junit.jupiter.api.Test;

class DemoDataGeneratorTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 9, 21);

    @Test
    void isDeterministicAndNeverInTheFuture() {
        List<DemoTransaction> first = DemoDataGenerator.generate(TODAY, 42);
        assertThat(DemoDataGenerator.generate(TODAY, 42)).isEqualTo(first);
        assertThat(DemoDataGenerator.generate(TODAY, 43)).isNotEqualTo(first);
        assertThat(first).allSatisfy(t -> {
            assertThat(t.date()).isBeforeOrEqualTo(TODAY);
            assertThat(t.amount()).isPositive();
        });
        assertThat(first.get(0).date()).isEqualTo(LocalDate.of(2026, 3, 1)); // six months back plus this month
    }

    @Test
    void givesEveryFeatureSomethingToShow() {
        List<DemoTransaction> rows = DemoDataGenerator.generate(TODAY, 7);
        YearMonth august = YearMonth.of(2026, 8);

        // Salary every month, including this one.
        assertThat(rows.stream().filter(t -> t.merchant().contains("Payroll")).count()).isEqualTo(7);
        // Monthly subscriptions for recurring detection.
        assertThat(rows.stream().filter(t -> t.merchant().equals("Netflix")).count()).isEqualTo(7);
        // One unusual large purchase last month for anomaly detection.
        assertThat(rows).filteredOn(t -> t.merchant().equals("Croma"))
                .singleElement().satisfies(t -> assertThat(YearMonth.from(t.date())).isEqualTo(august));
        // Food delivery rose last month.
        BigDecimal lastMonth = delivery(rows, august);
        BigDecimal before = delivery(rows, august.minusMonths(1));
        assertThat(lastMonth).isGreaterThan(before);
        // Income exceeds spending in a normal month so there are savings for goals.
        BigDecimal income = total(rows, august.minusMonths(1), "credit");
        BigDecimal spending = total(rows, august.minusMonths(1), "debit");
        assertThat(income).isGreaterThan(spending);
    }

    private static BigDecimal delivery(List<DemoTransaction> rows, YearMonth month) {
        return rows.stream().filter(t -> YearMonth.from(t.date()).equals(month))
                .filter(t -> t.merchant().equals("Zomato") || t.merchant().equals("Swiggy"))
                .map(DemoTransaction::amount).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private static BigDecimal total(List<DemoTransaction> rows, YearMonth month, String type) {
        return rows.stream().filter(t -> YearMonth.from(t.date()).equals(month) && t.type().equals(type))
                .map(DemoTransaction::amount).reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}

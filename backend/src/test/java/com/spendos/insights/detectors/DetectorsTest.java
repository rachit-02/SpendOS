package com.spendos.insights.detectors;

import static org.assertj.core.api.Assertions.assertThat;

import com.spendos.insights.detectors.SpendingContext.Tx;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class DetectorsTest {

    private static final YearMonth MONTH = YearMonth.of(2026, 8);
    private static final UUID FOOD = UUID.randomUUID();
    private static final UUID SHOPPING = UUID.randomUUID();
    private static final UUID ZOMATO = UUID.randomUUID();
    private static final UUID AMAZON = UUID.randomUUID();

    private static Tx tx(LocalDate date, String amount, UUID category, String categoryName, UUID merchant, String merchantName) {
        return new Tx(UUID.randomUUID(), date, new BigDecimal(amount), category, categoryName, merchant, merchantName);
    }

    private static List<YearMonth> baselineMonths(int count) {
        List<YearMonth> months = new ArrayList<>();
        for (int i = count; i >= 1; i--) {
            months.add(MONTH.minusMonths(i));
        }
        return months;
    }

    private static SpendingContext context(List<Tx> current, List<Tx> history, int baselineMonths) {
        return context(current, history, baselineMonths, BigDecimal.ZERO, 0);
    }

    private static SpendingContext context(List<Tx> current, List<Tx> history, int baselineMonths,
                                           BigDecimal recurringMonthly, int recurringCount) {
        return new SpendingContext(UUID.randomUUID(), MONTH, MONTH.atDay(1), MONTH.atEndOfMonth(), BigDecimal.ONE,
                current, history, baselineMonths(baselineMonths), BigDecimal.ZERO, "INR", recurringMonthly, recurringCount);
    }

    /** n purchases of the given amount spread over the month. */
    private static List<Tx> smallOrders(YearMonth month, int n, String amount) {
        List<Tx> orders = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            orders.add(tx(month.atDay(1 + (i % 28)), amount, FOOD, "Food", ZOMATO, "Zomato"));
        }
        return orders;
    }

    @Test
    void moneyLeakMatchesTheProductSpecExample() {
        // 18 orders totalling 1860 vs a three-month average of 1310 -> 42% more
        List<Tx> current = new ArrayList<>(smallOrders(MONTH, 17, "100"));
        current.add(tx(MONTH.atDay(28), "160", FOOD, "Food", ZOMATO, "Zomato"));
        List<Tx> history = new ArrayList<>();
        history.addAll(smallOrders(MONTH.minusMonths(1), 13, "100"));
        history.addAll(smallOrders(MONTH.minusMonths(2), 13, "100"));
        history.addAll(smallOrders(MONTH.minusMonths(3), 13, "101.8181"));

        List<InsightCandidate> leaks = new MoneyLeakDetector(BigDecimal.valueOf(500)).detect(context(current, history, 3));

        assertThat(leaks).singleElement().satisfies(leak -> {
            assertThat(leak.type()).isEqualTo("money_leak");
            assertThat(leak.description()).isEqualTo(
                    "You spent ₹1,860 across 18 small purchases at Zomato this month, 42% more than your three-month average.");
            assertThat(leak.transactionIds()).hasSize(18);
            assertThat(leak.suggestedAction()).contains("₹930");
            assertThat(leak.confidence()).isEqualByComparingTo("0.90");
        });
    }

    @Test
    void steadySmallPurchasesAreNotALeak() {
        List<Tx> history = new ArrayList<>();
        for (int m = 1; m <= 3; m++) {
            history.addAll(smallOrders(MONTH.minusMonths(m), 18, "100"));
        }
        assertThat(new MoneyLeakDetector(BigDecimal.valueOf(500))
                .detect(context(smallOrders(MONTH, 18, "100"), history, 3))).isEmpty();
    }

    @Test
    void categoryAnomalyUsesTheUsersOwnNormalRange() {
        List<Tx> history = new ArrayList<>();
        String[] monthly = {"1500", "2000", "2500", "2000"};
        for (int i = 0; i < monthly.length; i++) {
            history.add(tx(MONTH.minusMonths(4 - i).atDay(5), monthly[i], FOOD, "Food", ZOMATO, "Zomato"));
        }
        List<Tx> current = List.of(tx(MONTH.atDay(5), "4200", FOOD, "Food", ZOMATO, "Zomato"));

        List<InsightCandidate> anomalies = new AnomalyDetector(AnomalyDetector.Sensitivity.MEDIUM)
                .detect(context(current, history, 4));

        assertThat(anomalies).singleElement().satisfies(a -> {
            assertThat(a.description()).isEqualTo(
                    "You normally spend ₹1,646–₹2,354 on Food in a month. This month you've spent ₹4,200.");
            assertThat(a.impactPercentage()).isEqualByComparingTo("78");
        });
    }

    @Test
    void anomaliesNeedThreeMonthsOfHistoryAndRespectSensitivity() {
        List<Tx> shortHistory = List.of(tx(MONTH.minusMonths(1).atDay(5), "2000", FOOD, "Food", ZOMATO, "Zomato"));
        List<Tx> current = List.of(tx(MONTH.atDay(5), "9000", FOOD, "Food", ZOMATO, "Zomato"));
        assertThat(new AnomalyDetector(AnomalyDetector.Sensitivity.HIGH).detect(context(current, shortHistory, 2))).isEmpty();

        List<Tx> history = new ArrayList<>();
        for (int m = 1; m <= 4; m++) {
            history.add(tx(MONTH.minusMonths(m).atDay(5), "2000", FOOD, "Food", ZOMATO, "Zomato"));
        }
        // +40%: above HIGH's 25% bar but below MEDIUM's 50%
        List<Tx> slightlyHigh = List.of(tx(MONTH.atDay(5), "2800", FOOD, "Food", ZOMATO, "Zomato"));
        assertThat(new AnomalyDetector(AnomalyDetector.Sensitivity.HIGH).detect(context(slightlyHigh, history, 4))).hasSize(1);
        assertThat(new AnomalyDetector(AnomalyDetector.Sensitivity.MEDIUM).detect(context(slightlyHigh, history, 4))).isEmpty();
    }

    @Test
    void unusuallyLargePurchaseIsFlaggedAgainstTheCategoryMedian() {
        List<Tx> history = new ArrayList<>();
        String[] amounts = {"1500", "1700", "1600", "1800", "1900", "1700"};
        for (int i = 0; i < amounts.length; i++) {
            history.add(tx(MONTH.minusMonths(1 + i % 3).atDay(3 + i), amounts[i], SHOPPING, "Shopping", AMAZON, "Amazon"));
        }
        List<Tx> current = List.of(tx(MONTH.atDay(12), "8500", SHOPPING, "Shopping", AMAZON, "Amazon"));

        List<InsightCandidate> anomalies = new AnomalyDetector(AnomalyDetector.Sensitivity.MEDIUM)
                .largePurchases(context(current, history, 3));

        assertThat(anomalies).singleElement().satisfies(a -> {
            assertThat(a.description()).isEqualTo(
                    "You spent ₹8,500 at Amazon on Shopping, 5x your usual Shopping purchase of ₹1,700.");
            assertThat(a.transactionIds()).containsExactly(current.get(0).id());
        });
    }

    @Test
    void opportunitiesQuantifyTheSaving() {
        List<Tx> history = new ArrayList<>();
        for (int m = 1; m <= 3; m++) {
            history.add(tx(MONTH.minusMonths(m).atDay(4), "6000", FOOD, "Food", ZOMATO, "Zomato"));
        }
        List<Tx> current = List.of(tx(MONTH.atDay(4), "8500", FOOD, "Food", ZOMATO, "Zomato"));

        List<InsightCandidate> opportunities = new OpportunityDetector()
                .detect(context(current, history, 3, new BigDecimal("2117"), 5));

        assertThat(opportunities).extracting(InsightCandidate::title)
                .containsExactly("Food spending is up 42%", "Review your subscriptions");
        assertThat(opportunities.get(0).suggestedAction()).contains("₹2,500");
        assertThat(opportunities.get(1).description())
                .isEqualTo("You pay about ₹2,117 a month across 5 recurring payments (₹25,404 a year).");
    }

    @Test
    void spendingTrendComparesWithThePaceOfTheMonth() {
        List<Tx> history = new ArrayList<>();
        for (int m = 1; m <= 3; m++) {
            history.add(tx(MONTH.minusMonths(m).atDay(4), "30000", FOOD, "Food", ZOMATO, "Zomato"));
        }
        SpendingContext halfway = new SpendingContext(UUID.randomUUID(), MONTH, MONTH.atDay(1), MONTH.atDay(15),
                new BigDecimal("0.5"), List.of(tx(MONTH.atDay(4), "17700", FOOD, "Food", ZOMATO, "Zomato")), history,
                baselineMonths(3), BigDecimal.ZERO, "INR", BigDecimal.ZERO, 0);

        List<InsightCandidate> trends = new SpendingTrendDetector().detect(halfway);

        assertThat(trends).singleElement().satisfies(t -> {
            assertThat(t.title()).isEqualTo("Spending is 18% higher than usual");
            assertThat(t.description()).endsWith("than your three-month average of ₹15,000 for this point in the month.");
        });
    }
}

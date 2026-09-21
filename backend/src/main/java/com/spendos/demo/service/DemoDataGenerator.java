package com.spendos.demo.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Random;

/**
 * Synthetic, realistic personal-finance history for demo accounts (PRODUCT_SPEC.md "Demo Mode").
 * No real banking data: a salaried person in Bengaluru with rent, bills, subscriptions, groceries,
 * food delivery, commuting and shopping. The pattern is chosen so every feature has something to
 * show: recurring payments, a food-delivery increase last month, one unusually large purchase,
 * steady savings for goals. Deterministic for a given seed and date.
 */
public final class DemoDataGenerator {

    public record DemoTransaction(LocalDate date, String merchant, String category, BigDecimal amount, String type,
                                  String paymentMethod) {
    }

    static final int MONTHS_OF_HISTORY = 6;

    private DemoDataGenerator() {
    }

    public static List<DemoTransaction> generate(LocalDate today, long seed) {
        Random random = new Random(seed);
        List<DemoTransaction> rows = new ArrayList<>();
        YearMonth current = YearMonth.from(today);
        YearMonth lastComplete = current.minusMonths(1);
        for (YearMonth month = current.minusMonths(MONTHS_OF_HISTORY); !month.isAfter(current); month = month.plusMonths(1)) {
            boolean spike = month.equals(lastComplete);
            List<DemoTransaction> monthRows = new ArrayList<>();

            credit(monthRows, month, 1, "Acme Technologies Payroll", "Income", "85000", "neft");
            fixed(monthRows, month, 3, "Prestige Residency Rent", "Bills", "22000", "neft");
            fixed(monthRows, month, 8, "BESCOM", "Bills", between(random, 1400, 2300), "upi");
            fixed(monthRows, month, 10, "ACT Fibernet", "Bills", "999", "card");
            fixed(monthRows, month, 12, "Netflix", "Subscriptions", "649", "card");
            fixed(monthRows, month, 15, "Spotify", "Subscriptions", "119", "card");
            fixed(monthRows, month, 5, "Cult.fit", "Subscriptions", "1500", "card");
            fixed(monthRows, month, 20, "Airtel", "Bills", "599", "upi");

            repeat(monthRows, random, month, 6, 9, "BigBasket", "Food", 450, 1900, "upi");
            repeat(monthRows, random, month, 2, 4, "Blinkit", "Food", 180, 650, "upi");
            // Food delivery grows noticeably last month so the insights have a story to tell.
            repeat(monthRows, random, month, spike ? 16 : 9, spike ? 20 : 12, "Zomato", "Food", 220, 680, "upi");
            repeat(monthRows, random, month, spike ? 8 : 4, spike ? 10 : 6, "Swiggy", "Food", 200, 600, "upi");
            repeat(monthRows, random, month, 3, 5, "Starbucks", "Food", 280, 520, "card");
            repeat(monthRows, random, month, 6, 10, "Uber", "Transport", 140, 520, "upi");
            repeat(monthRows, random, month, 2, 4, "Ola", "Transport", 120, 380, "upi");
            repeat(monthRows, random, month, 1, 2, "Indian Oil", "Transport", 1800, 2800, "card");
            repeat(monthRows, random, month, 2, 4, "Amazon", "Shopping", 350, 3200, "card");
            repeat(monthRows, random, month, 0, 2, "Myntra", "Shopping", 800, 2600, "card");
            repeat(monthRows, random, month, 1, 2, "BookMyShow", "Entertainment", 300, 900, "upi");
            repeat(monthRows, random, month, 0, 1, "Apollo Pharmacy", "Healthcare", 250, 1200, "upi");
            if (spike) {
                // One large, unusual purchase for the anomaly detector.
                fixed(monthRows, month, 18, "Croma", "Shopping", "45999", "card");
            }
            if (month.getMonthValue() % 3 == 0) {
                credit(monthRows, month, 28, "Savings Account Interest", "Income", between(random, 900, 1400), "neft");
            }

            monthRows.stream().filter(row -> !row.date().isAfter(today)).forEach(rows::add);
        }
        rows.sort(Comparator.comparing(DemoTransaction::date).thenComparing(DemoTransaction::merchant));
        return rows;
    }

    private static void fixed(List<DemoTransaction> rows, YearMonth month, int day, String merchant, String category,
                              String amount, String method) {
        rows.add(new DemoTransaction(month.atDay(Math.min(day, month.lengthOfMonth())), merchant, category,
                new BigDecimal(amount), "debit", method));
    }

    private static void credit(List<DemoTransaction> rows, YearMonth month, int day, String merchant, String category,
                               String amount, String method) {
        rows.add(new DemoTransaction(month.atDay(Math.min(day, month.lengthOfMonth())), merchant, category,
                new BigDecimal(amount), "credit", method));
    }

    private static void repeat(List<DemoTransaction> rows, Random random, YearMonth month, int minCount, int maxCount,
                               String merchant, String category, int minAmount, int maxAmount, String method) {
        int count = minCount + random.nextInt(maxCount - minCount + 1);
        for (int i = 0; i < count; i++) {
            int day = 1 + random.nextInt(month.lengthOfMonth());
            rows.add(new DemoTransaction(month.atDay(day), merchant, category,
                    new BigDecimal(between(random, minAmount, maxAmount)), "debit", method));
        }
    }

    /** A whole-rupee amount, rounded to the nearest 10 so it looks like a real bill. */
    private static String between(Random random, int min, int max) {
        int value = min + random.nextInt(max - min + 1);
        return BigDecimal.valueOf(value).divide(BigDecimal.TEN, 0, RoundingMode.HALF_UP).multiply(BigDecimal.TEN)
                .max(BigDecimal.TEN).toPlainString();
    }
}

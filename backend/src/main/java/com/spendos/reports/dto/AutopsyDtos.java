package com.spendos.reports.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/** The monthly money autopsy (API_DESIGN.md "Get Monthly Autopsy"), plus next month's watch list. */
public final class AutopsyDtos {

    private AutopsyDtos() {
    }

    public record CategoryDelta(String category, BigDecimal previous, BigDecimal current, BigDecimal amount,
                                BigDecimal percentageChange) {
    }

    public record Changes(List<CategoryDelta> largestIncreases, List<CategoryDelta> largestDecreases) {
    }

    public record MerchantLine(String merchantName, BigDecimal amount, long count) {
    }

    public record CategoryLine(String categoryName, String colorHex, BigDecimal amount, BigDecimal percentage) {
    }

    public record RecurringLine(String merchantName, BigDecimal amount, String frequency, BigDecimal monthlyCost) {
    }

    public record UnusualLine(String description, BigDecimal amount, LocalDate date, String merchantName,
                              String categoryName) {
    }

    public record BudgetLine(BigDecimal budget, BigDecimal spent, BigDecimal percentage, boolean exceeded) {
    }

    public record WatchItem(String kind, String message, BigDecimal amount) {
    }

    public record MonthlyAutopsy(
            String period,
            int year,
            int month,
            LocalDate startDate,
            LocalDate endDate,
            boolean isComplete,
            String currencyCode,
            BigDecimal income,
            BigDecimal expenses,
            BigDecimal savings,
            BigDecimal savingsRate,
            BigDecimal previousExpenses,
            BigDecimal expenseChangePercentage,
            Integer healthScore,
            List<CategoryLine> spendingByCategory,
            Changes changes,
            List<MerchantLine> largestMerchants,
            List<RecurringLine> recurringPayments,
            List<UnusualLine> unusualTransactions,
            Map<String, BudgetLine> budgetPerformance,
            String mostImportantInsight,
            String suggestedAction,
            List<WatchItem> nextMonthWatchlist,
            long transactionCount,
            Instant generatedAt) {
    }

    public record ReportSummary(int year, int month, String period, boolean isComplete, Instant generatedAt) {
    }
}

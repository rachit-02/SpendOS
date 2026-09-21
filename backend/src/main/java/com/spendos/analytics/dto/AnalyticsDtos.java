package com.spendos.analytics.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** Response shapes for the /analytics endpoints (API_DESIGN.md "Analytics Endpoints"). */
public final class AnalyticsDtos {

    private AnalyticsDtos() {
    }

    public record PeriodInfo(String month, int monthNumber, int year, LocalDate startDate, LocalDate endDate) {
    }

    public record SourceAmount(String source, BigDecimal amount, BigDecimal percentage) {
    }

    public record CategoryBreakdown(UUID categoryId, String categoryName, String colorHex, BigDecimal amount,
                                    BigDecimal percentage, long count) {
    }

    public record MethodBreakdown(String method, BigDecimal amount, BigDecimal percentage, long count) {
    }

    public record CategoryChange(UUID categoryId, String categoryName, BigDecimal previous, BigDecimal current,
                                 BigDecimal change, BigDecimal changePercentage) {
    }

    public record Income(BigDecimal total, List<SourceAmount> bySource) {
    }

    public record Expenses(BigDecimal total, List<CategoryBreakdown> byCategory, List<MethodBreakdown> byPaymentMethod) {
    }

    public record Comparison(BigDecimal previousIncome, BigDecimal previousExpense, BigDecimal incomeChange,
                             BigDecimal expenseChange, String spendingTrend, List<CategoryChange> categoryChanges) {
    }

    public record MonthlyAnalytics(PeriodInfo period, Income income, Expenses expenses, BigDecimal savings,
                                   BigDecimal savingsRate, Comparison previousMonthComparison, String currencyCode) {
    }

    public record CategoryTrendPoint(String month, int monthNumber, int year, BigDecimal amount, long count) {
    }

    /**
     * {@code percentageChange}: latest month vs the average of the earlier months in the window.
     * {@code forecast}: weighted average of the last three months (weights 3:2:1, most recent heaviest).
     */
    public record CategoryTrend(UUID categoryId, String categoryName, List<CategoryTrendPoint> trend, BigDecimal average,
                                BigDecimal percentageChange, BigDecimal forecast, String forecastMethod) {
    }

    public record MerchantStat(UUID merchantId, String merchantName, BigDecimal amount, long count,
                               BigDecimal averageAmount, BigDecimal percentage) {
    }

    public record TrendMonth(String month, int monthNumber, int year, BigDecimal income, BigDecimal expense,
                             BigDecimal savings, BigDecimal savingsRate, BigDecimal lastYearIncome,
                             BigDecimal lastYearExpense, BigDecimal expenseChangeYearOverYear) {
    }

    public record Trends(List<TrendMonth> months, BigDecimal averageMonthlyIncome, BigDecimal averageMonthlyExpense,
                         TrendMonth highestSpendingMonth, TrendMonth lowestSpendingMonth) {
    }

    public record MonthSummary(PeriodInfo period, BigDecimal income, BigDecimal expense, BigDecimal savings,
                               BigDecimal savingsRate) {
    }

    public record MonthComparison(MonthSummary first, MonthSummary second, BigDecimal expenseChange,
                                  BigDecimal expenseChangePercentage, List<CategoryChange> categories) {
    }

    public record NamedAmount(String name, BigDecimal amount, long count) {
    }

    public record SubcategoryShare(String subcategoryName, BigDecimal amount, BigDecimal percentage) {
    }

    public record CategoryStatistics(UUID categoryId, String categoryName, LocalDate startDate, LocalDate endDate,
                                     BigDecimal totalAmount, long transactionCount, BigDecimal averageTransactionAmount,
                                     BigDecimal percentageOfTotal, List<NamedAmount> topMerchants,
                                     List<SubcategoryShare> subcategoryBreakdown) {
    }
}

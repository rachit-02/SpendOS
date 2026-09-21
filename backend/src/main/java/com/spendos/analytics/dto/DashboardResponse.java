package com.spendos.analytics.dto;

import com.spendos.health.service.HealthScoreCalculator;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Payload of GET /dashboard (API_DESIGN.md), plus the monthly trend used by the line chart. */
public record DashboardResponse(
        Period period,
        Summary summary,
        FinancialHealth financialHealth,
        Spending spending,
        List<TrendPoint> trend,
        List<RecentTransaction> recentTransactions,
        List<InsightPreview> insights,
        List<RecurringPreview> recurringPayments,
        List<BudgetPreview> budgets) {

    public record Period(LocalDate startDate, LocalDate endDate, String month, int monthNumber, int year,
                         boolean isCurrentMonth) {
    }

    public record Summary(BigDecimal totalIncome, BigDecimal totalExpense, BigDecimal netSavings, BigDecimal savingsRate,
                          String currencyCode, long transactionCount, BigDecimal expenseChangePercentage,
                          BigDecimal previousMonthExpense) {
    }

    public record FinancialHealth(Integer score, Map<String, BigDecimal> factors,
                                  List<HealthScoreCalculator.Factor> breakdown, List<Change> changes, String summary) {
    }

    public record Change(String factor, BigDecimal change, String reason) {
    }

    public record Spending(List<CategorySpend> byCategory, List<MerchantSpend> topMerchants) {
    }

    public record CategorySpend(UUID categoryId, String categoryName, String colorHex, BigDecimal amount,
                                BigDecimal percentage, long count, String trend, BigDecimal previousAmount) {
    }

    public record MerchantSpend(UUID merchantId, String merchantName, BigDecimal amount, long count) {
    }

    public record TrendPoint(String month, int monthNumber, int year, BigDecimal income, BigDecimal expense) {
    }

    public record RecentTransaction(UUID id, String merchantName, String categoryName, String categoryColor,
                                    BigDecimal amount, LocalDate transactionDate, String transactionType) {
    }

    public record InsightPreview(UUID id, String type, String title, String description, BigDecimal impactValue,
                                 boolean actionable) {
    }

    public record RecurringPreview(UUID id, String merchantName, BigDecimal typicalAmount, String frequency,
                                   LocalDate nextExpectedDate, boolean isUserConfirmed) {
    }

    public record BudgetPreview(UUID id, String budgetName, BigDecimal totalAmount, BigDecimal spentAmount,
                                BigDecimal percentage, BigDecimal remainingAmount, boolean isExceeded,
                                boolean isAlert, int alertThreshold) {
    }
}

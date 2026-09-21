package com.spendos.reports.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public final class PredictionDtos {

    private PredictionDtos() {
    }

    public record Range(BigDecimal min, BigDecimal max, BigDecimal median) {
    }

    /** Month-end spending forecast (API_DESIGN.md "Get Spending Prediction"). */
    public record SpendingPrediction(
            LocalDate currentDate, int month, int year, BigDecimal currentSpending, int daysElapsed,
            int remainingDaysInMonth, Range projectedMonthEnd, BigDecimal confidence, String confidenceReason,
            boolean sufficientData, String methodology, BigDecimal upcomingRecurring, BigDecimal dailyAverage,
            BigDecimal previousMonthTotal, BigDecimal monthlyAverage, String currencyCode) {
    }

    public record AffordabilityRequest(
            @NotNull @Positive @DecimalMax("99999999.99") @Digits(integer = 8, fraction = 2) BigDecimal purchaseAmount,
            @Size(max = 255) String purchaseDescription) {
    }

    public record Purchase(BigDecimal amount, String description) {
    }

    public record Verdict(boolean isAffordable, String confidence) {
    }

    public record Analysis(BigDecimal currentMonthSpending, BigDecimal expectedIncome, BigDecimal upcomingRecurring,
                           BigDecimal expectedRemainingExpenses, BigDecimal availableAfterPurchase,
                           BigDecimal projectedSavingsWithoutPurchase, BigDecimal projectedSavingsWithPurchase,
                           BigDecimal savingsImpact, BigDecimal monthlyGoalContributions,
                           BigDecimal remainingBudget) {
    }

    public record Affordability(Purchase purchase, Verdict affordability, Analysis analysis, List<String> considerations,
                                String explanation, String disclaimer) {
    }
}

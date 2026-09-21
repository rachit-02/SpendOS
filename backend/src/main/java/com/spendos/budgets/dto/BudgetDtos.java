package com.spendos.budgets.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public final class BudgetDtos {

    public static final String TYPE_PATTERN = "^(monthly|quarterly|annual|custom)$";

    private BudgetDtos() {
    }

    public record CategoryAllocation(
            @NotNull UUID categoryId,
            @NotNull @Positive @DecimalMax("99999999.99") @Digits(integer = 8, fraction = 2) BigDecimal allocatedAmount) {
    }

    /**
     * {@code endDate} may be omitted for monthly/quarterly/annual budgets and is then derived from
     * {@code startDate}. Allocations may not exceed the total.
     */
    public record BudgetRequest(
            @NotBlank @Size(max = 255) String budgetName,
            @Pattern(regexp = TYPE_PATTERN, message = "budgetType must be monthly, quarterly, annual or custom") String budgetType,
            @NotNull @Positive @DecimalMax("99999999.99") @Digits(integer = 8, fraction = 2) BigDecimal totalAmount,
            @NotNull LocalDate startDate,
            LocalDate endDate,
            @Min(1) @Max(100) Integer alertThreshold,
            Boolean isActive,
            @Size(max = 20) List<@Valid CategoryAllocation> categories) {
    }

    public record CategoryProgress(UUID categoryId, String categoryName, String colorHex, BigDecimal allocatedAmount,
                                   BigDecimal spentAmount, BigDecimal remainingAmount, BigDecimal percentage,
                                   boolean isExceeded, boolean isAlert) {
    }

    public record BudgetResponse(
            UUID id, String budgetName, String budgetType, BigDecimal totalAmount, String currencyCode,
            LocalDate startDate, LocalDate endDate, int alertThreshold, boolean isActive,
            BigDecimal spentAmount, BigDecimal remainingAmount, BigDecimal percentage, boolean isExceeded,
            boolean isAlert, String status, List<CategoryProgress> categories, Instant createdAt, Instant updatedAt) {
    }

    /** Detailed progress: pacing through the period and a straight-line projection to its end. */
    public record BudgetProgress(
            BudgetResponse budget, int daysElapsed, int daysTotal, int daysRemaining, BigDecimal expectedSpendToDate,
            BigDecimal projectedSpend, boolean projectedToExceed, BigDecimal dailyAllowanceRemaining, String pace) {
    }

    public record BudgetAlert(UUID budgetId, String budgetName, UUID categoryId, String categoryName,
                              BigDecimal limit, BigDecimal spent, BigDecimal percentage, String level, String message) {
    }
}

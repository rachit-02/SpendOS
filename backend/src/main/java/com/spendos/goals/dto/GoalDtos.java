package com.spendos.goals.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public final class GoalDtos {

    private GoalDtos() {
    }

    public record GoalRequest(
            @NotBlank @Size(max = 255) String goalName,
            @Size(max = 1000) String goalDescription,
            @Pattern(regexp = "^(savings|debt_payoff|expense_reduction)$") String goalType,
            @NotNull @Positive @DecimalMax("99999999.99") @Digits(integer = 8, fraction = 2) BigDecimal targetAmount,
            @DecimalMin("0") @DecimalMax("99999999.99") @Digits(integer = 8, fraction = 2) BigDecimal currentProgress,
            @NotNull LocalDate targetDate,
            Boolean isActive) {
    }

    public record ContributionRequest(
            @NotNull @DecimalMin(value = "-99999999.99") @DecimalMax("99999999.99") @Digits(integer = 8, fraction = 2)
            BigDecimal amount) {
    }

    /**
     * {@code monthlyContributionNeeded}: what must be saved each month to hit the target date.
     * {@code projectedCompletionDate}: when the goal is reached if all of the user's average monthly
     * savings went to it (null when the user is not saving).
     */
    public record GoalResponse(UUID id, String goalName, String goalDescription, String goalType, BigDecimal targetAmount,
                               BigDecimal currentProgress, BigDecimal remainingAmount, BigDecimal progressPercentage,
                               LocalDate targetDate, boolean isActive, Integer monthsToTarget,
                               BigDecimal monthlyContributionNeeded, LocalDate projectedCompletionDate, boolean onTrack,
                               String currencyCode, Instant createdAt) {
    }
}

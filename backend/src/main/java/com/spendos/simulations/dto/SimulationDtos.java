package com.spendos.simulations.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class SimulationDtos {

    public static final String TYPE_PATTERN =
            "^(spend_reduction|spend_increase|income_change|savings_increase|one_time_purchase)$";

    private SimulationDtos() {
    }

    /**
     * spend_reduction / spend_increase: change monthly spending (optionally in one category);
     * income_change: change monthly income (may be negative); savings_increase: move an amount from
     * spending to savings every month; one_time_purchase: a single purchase this year.
     */
    public record Scenario(
            @NotNull @Pattern(regexp = TYPE_PATTERN, message = "Unknown scenario type") String type,
            UUID categoryId,
            @NotNull @DecimalMin("-99999999.99") @DecimalMax("99999999.99") @Digits(integer = 8, fraction = 2) BigDecimal amount,
            @Pattern(regexp = "^(monthly|one_time)$") String period,
            @Size(max = 255) String description) {
    }

    public record SimulationRequest(@NotBlank @Size(max = 255) String simulationName,
                                    @NotEmpty @Size(max = 10) List<@Valid Scenario> scenarios) {
    }

    public record Impact(BigDecimal before, BigDecimal after, BigDecimal change, BigDecimal changePercentage) {
    }

    public record GoalImpact(UUID goalId, String goalName, Integer currentMonthsToCompletion,
                             Integer projectedMonthsWithSimulation) {
    }

    public record Results(Impact monthlyImpact, Impact annualImpact, Impact monthlySavings, Impact annualSavings,
                          List<GoalImpact> goalImpact, List<String> notes, int baselineMonths) {
    }

    public record SimulationResponse(UUID id, String simulationName, List<Scenario> scenarios, Results results,
                                     Instant createdAt) {
    }

    public record Comparison(List<SimulationResponse> simulations, UUID bestForSavingsId) {
    }
}

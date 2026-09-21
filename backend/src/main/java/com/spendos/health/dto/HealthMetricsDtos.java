package com.spendos.health.dto;

import com.spendos.health.service.HealthScoreCalculator.Factor;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;

public final class HealthMetricsDtos {

    private HealthMetricsDtos() {
    }

    /** Percentages are 0-100 (e.g. savingsRate 43.6); buffer is in months. Null means "no data". */
    public record Metrics(BigDecimal savingsRate, BigDecimal averageMonthlyIncome, BigDecimal averageMonthlyExpense,
                          BigDecimal spendingVolatility, BigDecimal recurringBurden, BigDecimal emergencyBufferMonths) {
    }

    /** An improvement with the concrete amount involved, highest potential gain first. */
    public record Recommendation(String factor, String title, String action, BigDecimal potentialPoints) {
    }

    public record HealthMetrics(boolean enabled, String period, Integer score, String summary, List<Factor> factors,
                                Metrics metrics, List<Recommendation> recommendations, Instant calculatedAt) {
    }

    public record HistoryPoint(String period, int year, int month, Integer score, Map<String, BigDecimal> factors) {
    }

    public record FactorChange(String factor, String label, BigDecimal previousPoints, BigDecimal currentPoints,
                               BigDecimal change, String reason) {
    }

    public record Explanation(boolean enabled, String period, String previousPeriod, Integer score, Integer previousScore,
                              Integer change, String summary, List<FactorChange> changes,
                              List<Recommendation> recommendations) {
    }
}

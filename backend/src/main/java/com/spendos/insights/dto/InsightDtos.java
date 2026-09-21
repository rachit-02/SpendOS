package com.spendos.insights.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class InsightDtos {

    private InsightDtos() {
    }

    public record RelatedTransaction(UUID id, BigDecimal amount, String merchant, String categoryName, LocalDate date) {
    }

    public record InsightResponse(UUID id, String type, String title, String description, BigDecimal impactValue,
                                  BigDecimal impactPercentage, UUID categoryId, String categoryName, UUID merchantId,
                                  String merchantName, boolean actionable, String suggestedAction, BigDecimal confidence,
                                  BigDecimal importance, LocalDate periodStartDate, LocalDate periodEndDate,
                                  Instant createdAt, Map<String, Object> details,
                                  List<RelatedTransaction> relatedTransactions) {
    }

    public record NormalRange(BigDecimal min, BigDecimal max) {
    }

    /** Anomaly view of GET /insights/anomalies (API_DESIGN.md). */
    public record AnomalyResponse(String kind, UUID categoryId, String categoryName, NormalRange normalRange,
                                  BigDecimal currentAmount, BigDecimal percentageAboveNormal, String period,
                                  int transactionCount, String description, List<RelatedTransaction> relatedTransactions) {
    }
}

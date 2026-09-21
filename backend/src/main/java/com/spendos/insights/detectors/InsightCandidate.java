package com.spendos.insights.detectors;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** A finding produced by a detector, before it is scored and stored. */
public record InsightCandidate(
        String type,
        String title,
        String description,
        BigDecimal impactValue,
        BigDecimal impactPercentage,
        UUID categoryId,
        UUID merchantId,
        boolean actionable,
        String suggestedAction,
        BigDecimal confidence,
        List<UUID> transactionIds,
        Map<String, Object> details) {
}

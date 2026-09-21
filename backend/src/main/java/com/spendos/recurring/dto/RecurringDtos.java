package com.spendos.recurring.dto;

import com.spendos.common.util.Times;
import com.spendos.recurring.domain.RecurringPayment;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public final class RecurringDtos {

    private RecurringDtos() {
    }

    /** {@code status}: pending (awaiting review), confirmed, dismissed, or lapsed (no longer seen). */
    public record RecurringResponse(UUID id, UUID merchantId, String merchantName, UUID categoryId,
                                    BigDecimal typicalAmount, String currencyCode, String frequency,
                                    LocalDate nextExpectedDate, LocalDate lastOccurrenceDate, int occurrencesCount,
                                    BigDecimal confidence, boolean isActive, boolean isUserConfirmed, String status,
                                    BigDecimal monthlyCost, Instant detectedAt) {

        public static RecurringResponse from(RecurringPayment p) {
            String status = p.isUserConfirmed() && !p.isActive() ? "dismissed"
                    : p.isUserConfirmed() ? "confirmed"
                    : !p.isActive() ? "lapsed" : "pending";
            return new RecurringResponse(p.getId(), p.getMerchantId(), p.getMerchantName(), p.getCategoryId(),
                    p.getTypicalAmount(), p.getCurrencyCode(), p.getFrequency(), p.getNextExpectedDate(),
                    p.getLastOccurrenceDate(), p.getOccurrencesCount(), p.getConfidence(), p.isActive(),
                    p.isUserConfirmed(), status, RecurringDtos.monthlyCost(p.getTypicalAmount(), p.getFrequency()),
                    Times.utc(p.getDetectedAt()));
        }
    }

    public record DecisionResponse(UUID id, boolean isUserConfirmed, boolean isActive, Instant decidedAt) {
    }

    public record DetectionSummary(int detected, int created, int updated, int active) {
    }

    /** Normalizes any frequency to an approximate monthly cost (e.g. annual / 12). */
    public static BigDecimal monthlyCost(BigDecimal amount, String frequency) {
        if (amount == null) {
            return null;
        }
        BigDecimal factor = switch (frequency) {
            case "daily" -> new BigDecimal("30.44");
            case "weekly" -> new BigDecimal("4.345");
            case "biweekly" -> new BigDecimal("2.1725");
            case "quarterly" -> new BigDecimal("0.3333333");
            case "annual" -> new BigDecimal("0.0833333");
            default -> BigDecimal.ONE;
        };
        return amount.multiply(factor).setScale(2, java.math.RoundingMode.HALF_UP);
    }
}

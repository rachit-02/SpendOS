package com.spendos.recurring.domain;

import com.spendos.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "recurring_payments")
@Getter
@Setter
@NoArgsConstructor
public class RecurringPayment extends BaseEntity {

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "merchant_id")
    private UUID merchantId;

    @Column(name = "category_id")
    private UUID categoryId;

    @Column(name = "merchant_name", length = 255)
    private String merchantName;

    @Column(name = "typical_amount", precision = 19, scale = 2)
    private BigDecimal typicalAmount;

    @Column(name = "currency_code", length = 3)
    private String currencyCode = "INR";

    @Column(name = "frequency", nullable = false, length = 50)
    private String frequency;

    @Column(name = "next_expected_date")
    private LocalDate nextExpectedDate;

    @Column(name = "last_occurrence_date")
    private LocalDate lastOccurrenceDate;

    @Column(name = "occurrences_count")
    private int occurrencesCount = 1;

    @Column(name = "confidence", precision = 3, scale = 2)
    private BigDecimal confidence;

    @Column(name = "is_active")
    private boolean active = true;

    @Column(name = "is_user_confirmed")
    private boolean userConfirmed;

    @Column(name = "detected_at", nullable = false)
    private LocalDateTime detectedAt = LocalDateTime.now(ZoneOffset.UTC);
}

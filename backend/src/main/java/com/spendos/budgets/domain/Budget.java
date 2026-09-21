package com.spendos.budgets.domain;

import com.spendos.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Set;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "budgets")
@Getter
@Setter
@NoArgsConstructor
public class Budget extends BaseEntity {

    public static final Set<String> TYPES = Set.of("monthly", "quarterly", "annual", "custom");

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "budget_name", nullable = false, length = 255)
    private String budgetName;

    @Column(name = "budget_type", length = 50)
    private String budgetType = "monthly";

    @Column(name = "total_amount", nullable = false, precision = 19, scale = 2)
    private BigDecimal totalAmount;

    @Column(name = "currency_code", length = 3)
    private String currencyCode = "INR";

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "end_date", nullable = false)
    private LocalDate endDate;

    @Column(name = "alert_threshold_percent")
    private Integer alertThresholdPercent = 90;

    @Column(name = "is_active")
    private boolean active = true;
}

package com.spendos.health.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** Latest computed health score per user (one row per user), cached for the dashboard. */
@Entity
@Table(name = "financial_health_metrics")
@Getter
@Setter
@NoArgsConstructor
public class FinancialHealthMetrics {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "user_id", nullable = false, unique = true)
    private UUID userId;

    @Column(name = "health_score")
    private Integer healthScore;

    @Column(name = "savings_rate", precision = 5, scale = 2)
    private BigDecimal savingsRate;

    @Column(name = "average_monthly_income", precision = 19, scale = 2)
    private BigDecimal averageMonthlyIncome;

    @Column(name = "average_monthly_expense", precision = 19, scale = 2)
    private BigDecimal averageMonthlyExpense;

    @Column(name = "spending_volatility", precision = 5, scale = 2)
    private BigDecimal spendingVolatility;

    @Column(name = "recurring_expense_burden", precision = 5, scale = 2)
    private BigDecimal recurringExpenseBurden;

    @Column(name = "emergency_buffer_months", precision = 5, scale = 2)
    private BigDecimal emergencyBufferMonths;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "score_factors", columnDefinition = "jsonb")
    private String scoreFactors;

    @Column(name = "calculated_at", nullable = false)
    private LocalDateTime calculatedAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}

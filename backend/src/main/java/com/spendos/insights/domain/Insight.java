package com.spendos.insights.domain;

import com.spendos.common.entity.CreatedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "insights")
@Getter
@Setter
@NoArgsConstructor
public class Insight extends CreatedEntity {

    public static final String SPENDING_TREND = "spending_trend";
    public static final String ANOMALY = "anomaly";
    public static final String MONEY_LEAK = "money_leak";
    public static final String OPPORTUNITY = "opportunity";

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "insight_type", nullable = false, length = 50)
    private String insightType;

    @Column(name = "title", nullable = false, length = 255)
    private String title;

    @Column(name = "description", nullable = false)
    private String description;

    @Column(name = "impact_value", precision = 19, scale = 2)
    private BigDecimal impactValue;

    @Column(name = "impact_percentage", precision = 5, scale = 2)
    private BigDecimal impactPercentage;

    @Column(name = "related_category_id")
    private UUID relatedCategoryId;

    @Column(name = "related_merchant_id")
    private UUID relatedMerchantId;

    @Column(name = "actionable")
    private boolean actionable = true;

    @Column(name = "suggested_action")
    private String suggestedAction;

    @Column(name = "confidence", precision = 3, scale = 2)
    private BigDecimal confidence;

    @Column(name = "period_start_date", nullable = false)
    private LocalDate periodStartDate;

    @Column(name = "period_end_date", nullable = false)
    private LocalDate periodEndDate;

    @Column(name = "expires_at")
    private LocalDateTime expiresAt;

    /** JSON evidence: supporting transaction IDs, baselines, normal range. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "details", columnDefinition = "jsonb")
    private String details;

    @Column(name = "importance_score", precision = 6, scale = 2)
    private BigDecimal importanceScore = BigDecimal.ZERO;
}

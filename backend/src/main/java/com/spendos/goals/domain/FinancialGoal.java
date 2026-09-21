package com.spendos.goals.domain;

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
@Table(name = "financial_goals")
@Getter
@Setter
@NoArgsConstructor
public class FinancialGoal extends BaseEntity {

    public static final Set<String> TYPES = Set.of("savings", "debt_payoff", "expense_reduction");

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "goal_name", nullable = false, length = 255)
    private String goalName;

    @Column(name = "goal_description")
    private String goalDescription;

    @Column(name = "goal_type", length = 50)
    private String goalType;

    @Column(name = "target_amount", nullable = false, precision = 19, scale = 2)
    private BigDecimal targetAmount;

    @Column(name = "current_progress", precision = 19, scale = 2)
    private BigDecimal currentProgress = BigDecimal.ZERO;

    @Column(name = "currency_code", length = 3)
    private String currencyCode = "INR";

    @Column(name = "target_date", nullable = false)
    private LocalDate targetDate;

    @Column(name = "priority")
    private Integer priority = 100;

    @Column(name = "is_active")
    private boolean active = true;
}

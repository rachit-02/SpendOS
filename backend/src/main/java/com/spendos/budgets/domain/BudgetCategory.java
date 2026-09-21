package com.spendos.budgets.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "budget_categories")
@Getter
@Setter
@NoArgsConstructor
public class BudgetCategory {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "budget_id", nullable = false)
    private UUID budgetId;

    @Column(name = "category_id", nullable = false)
    private UUID categoryId;

    @Column(name = "allocated_amount", nullable = false, precision = 19, scale = 2)
    private BigDecimal allocatedAmount;

    public BudgetCategory(UUID budgetId, UUID categoryId, BigDecimal allocatedAmount) {
        this.budgetId = budgetId;
        this.categoryId = categoryId;
        this.allocatedAmount = allocatedAmount;
    }
}

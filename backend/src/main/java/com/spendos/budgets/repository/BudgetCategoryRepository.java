package com.spendos.budgets.repository;

import com.spendos.budgets.domain.BudgetCategory;
import com.spendos.common.repository.BaseRepository;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface BudgetCategoryRepository extends BaseRepository<BudgetCategory> {
    List<BudgetCategory> findByBudgetId(UUID budgetId);

    List<BudgetCategory> findByBudgetIdIn(Collection<UUID> budgetIds);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("DELETE FROM BudgetCategory bc WHERE bc.budgetId = :budgetId")
    void deleteByBudgetId(@Param("budgetId") UUID budgetId);
}

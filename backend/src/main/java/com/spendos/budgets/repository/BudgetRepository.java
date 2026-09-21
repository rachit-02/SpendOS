package com.spendos.budgets.repository;

import com.spendos.budgets.domain.Budget;
import com.spendos.common.repository.BaseRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface BudgetRepository extends BaseRepository<Budget> {
    List<Budget> findByUserId(UUID userId);

    Optional<Budget> findByIdAndUserId(UUID id, UUID userId);
}

package com.spendos.goals.repository;

import com.spendos.common.repository.BaseRepository;
import com.spendos.goals.domain.FinancialGoal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface FinancialGoalRepository extends BaseRepository<FinancialGoal> {
    List<FinancialGoal> findByUserId(UUID userId);

    List<FinancialGoal> findByUserIdAndActiveTrue(UUID userId);

    Optional<FinancialGoal> findByIdAndUserId(UUID id, UUID userId);
}

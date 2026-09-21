package com.spendos.health.repository;

import com.spendos.common.repository.BaseRepository;
import com.spendos.health.domain.FinancialHealthMetrics;
import java.util.Optional;
import java.util.UUID;

public interface FinancialHealthMetricsRepository extends BaseRepository<FinancialHealthMetrics> {
    Optional<FinancialHealthMetrics> findByUserId(UUID userId);
}

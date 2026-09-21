package com.spendos.reports.repository;

import com.spendos.common.repository.BaseRepository;
import com.spendos.reports.domain.MonthlyReport;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface MonthlyReportRepository extends BaseRepository<MonthlyReport> {
    Optional<MonthlyReport> findByUserIdAndPeriodYearAndPeriodMonth(UUID userId, int periodYear, int periodMonth);

    List<MonthlyReport> findByUserIdOrderByPeriodYearDescPeriodMonthDesc(UUID userId);
}

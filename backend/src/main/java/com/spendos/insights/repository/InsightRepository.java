package com.spendos.insights.repository;

import com.spendos.common.repository.BaseRepository;
import com.spendos.insights.domain.Insight;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface InsightRepository extends BaseRepository<Insight> {
    List<Insight> findByUserIdOrderByCreatedAtDesc(UUID userId);

    Optional<Insight> findByIdAndUserId(UUID id, UUID userId);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("DELETE FROM Insight i WHERE i.userId = :userId AND i.periodStartDate = :start AND i.periodEndDate = :end")
    int deleteForPeriod(@Param("userId") UUID userId, @Param("start") LocalDate start, @Param("end") LocalDate end);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("DELETE FROM Insight i WHERE i.expiresAt IS NOT NULL AND i.expiresAt < :now")
    int deleteExpired(@Param("now") LocalDateTime now);
}

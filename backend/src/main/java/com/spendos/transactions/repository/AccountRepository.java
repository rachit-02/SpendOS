package com.spendos.transactions.repository;

import com.spendos.common.repository.BaseRepository;
import com.spendos.transactions.domain.Account;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AccountRepository extends BaseRepository<Account> {
    List<Account> findByUserIdOrderByPrimaryDescCreatedAtAsc(UUID userId);

    Optional<Account> findByIdAndUserId(UUID id, UUID userId);

    boolean existsByIdAndUserId(UUID id, UUID userId);

    long countByUserId(UUID userId);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("UPDATE Account a SET a.primary = false WHERE a.userId = :userId AND a.primary = true")
    int clearPrimary(@Param("userId") UUID userId);
}

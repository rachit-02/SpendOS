package com.spendos.transactions.repository;

import com.spendos.common.repository.BaseRepository;
import com.spendos.transactions.domain.Transaction;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Every finder is scoped by user ID: there is intentionally no way to load another user's rows. */
public interface TransactionRepository extends BaseRepository<Transaction>, JpaSpecificationExecutor<Transaction> {

    @EntityGraph(attributePaths = {"merchant", "category", "subcategory"})
    Optional<Transaction> findByIdAndUserId(UUID id, UUID userId);

    @Override
    @EntityGraph(attributePaths = {"merchant", "category", "subcategory"})
    Page<Transaction> findAll(Specification<Transaction> specification, Pageable pageable);

    @EntityGraph(attributePaths = {"merchant", "category", "subcategory"})
    List<Transaction> findByUserIdAndIdIn(UUID userId, Collection<UUID> ids);

    @EntityGraph(attributePaths = {"merchant", "category", "subcategory"})
    List<Transaction> findByUserIdAndTransactionDateBetweenOrderByTransactionDateAsc(
            UUID userId, LocalDate start, LocalDate end);

    List<Transaction> findByUserIdAndTransactionDateBetween(UUID userId, LocalDate start, LocalDate end);

    long countByUserId(UUID userId);

    boolean existsByAccountId(UUID accountId);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("UPDATE Transaction t SET t.duplicateOfId = null WHERE t.duplicateOfId IN :ids")
    int clearDuplicateReferences(@Param("ids") Collection<UUID> ids);
}

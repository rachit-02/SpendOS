package com.spendos.imports.repository;

import com.spendos.common.repository.BaseRepository;
import com.spendos.imports.domain.ImportJob;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface ImportJobRepository extends BaseRepository<ImportJob> {
    Page<ImportJob> findByUserId(UUID userId, Pageable pageable);

    Optional<ImportJob> findByIdAndUserId(UUID id, UUID userId);
}

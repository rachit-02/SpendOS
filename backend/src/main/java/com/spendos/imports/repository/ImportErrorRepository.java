package com.spendos.imports.repository;

import com.spendos.common.repository.BaseRepository;
import com.spendos.imports.domain.ImportError;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface ImportErrorRepository extends BaseRepository<ImportError> {
    Page<ImportError> findByImportJobIdOrderByRowNumberAsc(UUID importJobId, Pageable pageable);
}

package com.spendos.audit.repository;

import com.spendos.audit.domain.AuditLog;
import com.spendos.common.repository.BaseRepository;
import java.util.List;
import java.util.UUID;

public interface AuditLogRepository extends BaseRepository<AuditLog> {
    List<AuditLog> findByUserIdOrderByCreatedAtDesc(UUID userId);

    List<AuditLog> findByEntityTypeAndEntityIdOrderByCreatedAtAsc(String entityType, String entityId);
}

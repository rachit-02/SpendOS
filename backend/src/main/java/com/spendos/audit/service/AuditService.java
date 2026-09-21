package com.spendos.audit.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.spendos.audit.domain.AuditLog;
import com.spendos.audit.repository.AuditLogRepository;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * Writes the append-only audit trail (audit_logs). Callers pass only the fields that matter for the
 * change; passwords, tokens and full card numbers must never be included.
 */
@Service
public class AuditService {

    public static final String CREATE = "create";
    public static final String UPDATE = "update";
    public static final String DELETE = "delete";
    public static final String EXPORT = "export";

    private static final Logger log = LoggerFactory.getLogger(AuditService.class);

    private final AuditLogRepository repository;
    private final ObjectMapper objectMapper;

    public AuditService(AuditLogRepository repository, ObjectMapper objectMapper) {
        this.repository = repository;
        this.objectMapper = objectMapper;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void record(UUID userId, String entityType, Object entityId, String action,
                       Map<String, ?> oldValues, Map<String, ?> newValues) {
        AuditLog entry = new AuditLog();
        entry.setUserId(userId);
        entry.setEntityType(entityType);
        entry.setEntityId(String.valueOf(entityId));
        entry.setAction(action);
        entry.setOldValues(toJson(oldValues));
        entry.setNewValues(toJson(newValues));
        HttpServletRequest request = currentRequest();
        if (request != null) {
            entry.setIpAddress(truncate(request.getRemoteAddr(), 45));
            entry.setUserAgent(truncate(request.getHeader("User-Agent"), 500));
        }
        repository.save(entry);
        log.info("Audit | userId={} | entity={} | entityId={} | action={}", userId, entityType, entityId, action);
    }

    private String toJson(Map<String, ?> values) {
        if (values == null || values.isEmpty()) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(values);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Audit values not serializable", exception);
        }
    }

    private static HttpServletRequest currentRequest() {
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes) {
            return attributes.getRequest();
        }
        return null;
    }

    private static String truncate(String value, int max) {
        return value == null || value.length() <= max ? value : value.substring(0, max);
    }
}

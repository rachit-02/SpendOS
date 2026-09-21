package com.spendos.common.dto;

import com.spendos.common.web.RequestIdFilter;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record ErrorResponse(
        boolean success,
        ErrorBody error,
        Instant timestamp,
        UUID requestId) {

    public ErrorResponse(String code, String message, Map<String, ?> details) {
        this(false, new ErrorBody(code, message, details == null || details.isEmpty() ? null : details),
                Instant.now(), RequestIdFilter.currentRequestId());
    }

    public record ErrorBody(String code, String message, Map<String, ?> details) {
    }
}

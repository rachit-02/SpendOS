package com.spendos.common.dto;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record ErrorResponse(
        boolean success,
        ErrorBody error,
        Instant timestamp,
        UUID requestId) {

    public ErrorResponse(String code, String message, Map<String, String> details) {
        this(false, new ErrorBody(code, message, details), Instant.now(), UUID.randomUUID());
    }

    public record ErrorBody(String code, String message, Map<String, String> details) {
    }
}

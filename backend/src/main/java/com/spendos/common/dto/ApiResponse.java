package com.spendos.common.dto;

import java.time.Instant;
import java.util.UUID;

public record ApiResponse<T>(boolean success, T data, Instant timestamp, UUID requestId) {
    public static <T> ApiResponse<T> success(T data) {
        return new ApiResponse<>(true, data, Instant.now(), UUID.randomUUID());
    }
}

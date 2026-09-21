package com.spendos.common.dto;

import com.spendos.common.web.RequestIdFilter;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;

/** Success envelope from API_DESIGN.md. {@code pagination} is omitted (null) for non-list responses. */
public record ApiResponse<T>(boolean success, T data, PaginationInfo pagination, Instant timestamp, UUID requestId) {

    public static <T> ApiResponse<T> success(T data) {
        return new ApiResponse<>(true, data, null, Instant.now(), RequestIdFilter.currentRequestId());
    }

    public static <T> ApiResponse<List<T>> page(Page<T> page) {
        return new ApiResponse<>(true, page.getContent(), PaginationInfo.from(page), Instant.now(),
                RequestIdFilter.currentRequestId());
    }
}

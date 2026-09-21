package com.spendos.common.util;

import com.spendos.common.exception.ApiException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

/** Builds Spring page requests from the API's 1-based {@code page}/{@code pageSize} parameters. */
public final class PageRequests {

    public static final int MAX_PAGE_SIZE = 100;

    private PageRequests() {
    }

    public static PageRequest of(int page, int pageSize, Sort sort) {
        if (page < 1) {
            throw ApiException.badRequest("INVALID_REQUEST", "page must be >= 1");
        }
        if (pageSize < 1 || pageSize > MAX_PAGE_SIZE) {
            throw ApiException.badRequest("INVALID_REQUEST", "pageSize must be between 1 and " + MAX_PAGE_SIZE);
        }
        return PageRequest.of(page - 1, pageSize, sort);
    }

    public static Sort.Direction direction(String sortOrder) {
        if (sortOrder == null || sortOrder.isBlank() || "desc".equalsIgnoreCase(sortOrder)) {
            return Sort.Direction.DESC;
        }
        if ("asc".equalsIgnoreCase(sortOrder)) {
            return Sort.Direction.ASC;
        }
        throw ApiException.badRequest("INVALID_REQUEST", "sortOrder must be 'asc' or 'desc'");
    }
}

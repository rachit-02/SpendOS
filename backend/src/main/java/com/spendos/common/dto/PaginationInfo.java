package com.spendos.common.dto;

import org.springframework.data.domain.Page;

/** Pagination block of the list response envelope. {@code currentPage} is 1-based. */
public record PaginationInfo(
        long totalItems, int totalPages, int currentPage, int pageSize, boolean hasNext, boolean hasPrevious) {

    public static PaginationInfo from(Page<?> page) {
        return new PaginationInfo(page.getTotalElements(), page.getTotalPages(), page.getNumber() + 1,
                page.getSize(), page.hasNext(), page.hasPrevious());
    }
}

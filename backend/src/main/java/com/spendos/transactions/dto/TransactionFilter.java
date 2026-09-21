package com.spendos.transactions.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/** Query parameters of GET /transactions (API_DESIGN.md). All fields optional. */
public record TransactionFilter(
        LocalDate startDate,
        LocalDate endDate,
        UUID categoryId,
        UUID merchantId,
        UUID accountId,
        BigDecimal minAmount,
        BigDecimal maxAmount,
        String transactionType,
        String paymentMethod,
        String searchText,
        Boolean recurring) {

    public static TransactionFilter empty() {
        return new TransactionFilter(null, null, null, null, null, null, null, null, null, null, null);
    }
}

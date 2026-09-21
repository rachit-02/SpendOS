package com.spendos.merchants.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public final class MerchantDtos {

    private MerchantDtos() {
    }

    /**
     * A merchant as this user sees it. {@code categoryId}/{@code categoryName} are the user's own
     * category for it when they have set one ({@code userCategory} true), otherwise the global default.
     * Usage figures cover only this user's transactions.
     */
    public record MerchantResponse(UUID id, String merchantName, UUID categoryId, String categoryName, String logoUrl,
                                   String website, boolean isVerified, BigDecimal confidenceScore, boolean userCategory,
                                   long transactionCount, BigDecimal averageTransactionAmount,
                                   LocalDate lastTransaction) {
    }

    public record MappingRequest(
            @NotBlank @Size(max = 255) String rawMerchantName,
            @NotNull UUID normalizedMerchantId,
            UUID categoryId) {
    }

    /** {@code appliedToTransactions}: how many existing transactions the correction updated. */
    public record MappingResponse(UUID id, String rawMerchantName, UUID normalizedMerchantId,
                                  String normalizedMerchantName, UUID categoryId, String categoryName, long usageCount,
                                  Integer appliedToTransactions, Instant createdAt, Instant updatedAt) {
    }

    public record CategoryUpdateRequest(@NotNull UUID categoryId) {
    }

    /** A likely duplicate: one of the user's unrecognised merchants that looks like a known one. */
    public record MerchantSuggestion(UUID merchantId, String merchantName, long transactionCount,
                                     UUID suggestedMerchantId, String suggestedMerchantName, UUID suggestedCategoryId,
                                     String suggestedCategoryName, BigDecimal similarity) {
    }
}

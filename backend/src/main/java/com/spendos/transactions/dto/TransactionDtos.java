package com.spendos.transactions.dto;

import com.spendos.common.util.Times;
import com.spendos.transactions.domain.Transaction;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public final class TransactionDtos {

    public static final String TYPE_PATTERN = "^(debit|credit|transfer)$";
    public static final String PAYMENT_METHOD_PATTERN = "^(upi|card|net_banking|cash|wallet)$";

    private TransactionDtos() {
    }

    public record CreateTransactionRequest(
            @NotNull(message = "accountId is required") UUID accountId,
            @NotNull(message = "merchantName is required") @Size(min = 1, max = 255) String merchantName,
            UUID categoryId,
            UUID subcategoryId,
            @NotNull(message = "Amount is required") @Positive(message = "Amount must be positive")
            @DecimalMax(value = "99999999.99", message = "Amount exceeds maximum allowed")
            @Digits(integer = 8, fraction = 2, message = "Amount must have at most 2 decimal places") BigDecimal amount,
            @Pattern(regexp = "^[A-Z]{3}$", message = "currencyCode must be a 3-letter ISO code") String currencyCode,
            @NotNull(message = "transactionType is required")
            @Pattern(regexp = TYPE_PATTERN, message = "transactionType must be debit, credit or transfer") String transactionType,
            @NotNull(message = "transactionDate is required") LocalDate transactionDate,
            @Size(max = 1000) String description,
            @Pattern(regexp = PAYMENT_METHOD_PATTERN, message = "Invalid payment method") String paymentMethod,
            @Size(max = 255) String externalReference) {
    }

    /** Partial update: null fields are left unchanged. */
    public record UpdateTransactionRequest(
            UUID accountId,
            @Size(min = 1, max = 255) String merchantName,
            UUID categoryId,
            UUID subcategoryId,
            @Positive(message = "Amount must be positive")
            @DecimalMax(value = "99999999.99", message = "Amount exceeds maximum allowed")
            @Digits(integer = 8, fraction = 2) BigDecimal amount,
            @Pattern(regexp = TYPE_PATTERN, message = "transactionType must be debit, credit or transfer") String transactionType,
            LocalDate transactionDate,
            @Size(max = 1000) String description,
            @Pattern(regexp = PAYMENT_METHOD_PATTERN, message = "Invalid payment method") String paymentMethod,
            Boolean isRecurring) {
    }

    public record BulkUpdateRequest(
            @NotEmpty @Size(max = 500) List<@NotNull UUID> transactionIds,
            @NotNull @Valid BulkUpdates updates) {
    }

    public record BulkUpdates(UUID categoryId, UUID subcategoryId,
                              @Pattern(regexp = PAYMENT_METHOD_PATTERN) String paymentMethod,
                              Boolean isRecurring) {
    }

    public record BulkDeleteRequest(@NotEmpty @Size(max = 500) List<@NotNull UUID> transactionIds) {
    }

    public record BulkResult(int updated, int failed) {
    }

    public record BulkDeleteResult(int deleted, int failed) {
    }

    public record TransactionResponse(
            UUID id,
            UUID accountId,
            UUID merchantId,
            String merchantName,
            UUID categoryId,
            String categoryName,
            String categoryColor,
            UUID subcategoryId,
            String subcategoryName,
            BigDecimal amount,
            String currencyCode,
            String transactionType,
            LocalDate transactionDate,
            String description,
            String rawDescription,
            String paymentMethod,
            String externalReference,
            boolean isRecurring,
            boolean isTransfer,
            BigDecimal categorizationConfidence,
            String categorizationSource,
            boolean isDuplicate,
            Instant createdAt,
            Instant updatedAt) {

        public static TransactionResponse from(Transaction t) {
            return new TransactionResponse(
                    t.getId(), t.getAccountId(), t.getMerchantId(),
                    t.getMerchant() != null ? t.getMerchant().getMerchantName() : null,
                    t.getCategoryId(),
                    t.getCategory() != null ? t.getCategory().getCategoryName() : null,
                    t.getCategory() != null ? t.getCategory().getColorHex() : null,
                    t.getSubcategoryId(),
                    t.getSubcategory() != null ? t.getSubcategory().getSubcategoryName() : null,
                    t.getAmount(), t.getCurrencyCode(), t.getTransactionType(), t.getTransactionDate(),
                    t.getDescription(), t.getRawDescription(), t.getPaymentMethod(), t.getExternalReference(),
                    t.isRecurring(), t.isTransfer(), t.getCategorizationConfidence(), t.getCategorizationSource(),
                    t.isDuplicate(), Times.utc(t.getCreatedAt()), Times.utc(t.getUpdatedAt()));
        }
    }
}

package com.spendos.transactions.dto;

import com.spendos.common.util.Times;
import com.spendos.transactions.domain.Account;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public final class AccountDtos {

    public static final String TYPE_PATTERN = "^(savings|checking|credit|digital_wallet)$";

    private AccountDtos() {
    }

    public record CreateAccountRequest(
            @NotBlank @Size(max = 255) String accountName,
            @NotBlank @Pattern(regexp = TYPE_PATTERN, message = "accountType must be savings, checking, credit or digital_wallet")
            String accountType,
            @Pattern(regexp = "^\\d{4}$", message = "Only the last 4 digits may be stored") String accountNumberLast4,
            @Size(max = 255) String bankName,
            Boolean isPrimary,
            @Pattern(regexp = "^[A-Z]{3}$") String currencyCode,
            @DecimalMin("-99999999999.99") @DecimalMax("99999999999.99") BigDecimal openingBalance) {
    }

    /** Partial update: null fields are left unchanged. */
    public record UpdateAccountRequest(
            @Size(min = 1, max = 255) String accountName,
            @Pattern(regexp = TYPE_PATTERN) String accountType,
            @Pattern(regexp = "^\\d{4}$", message = "Only the last 4 digits may be stored") String accountNumberLast4,
            @Size(max = 255) String bankName,
            Boolean isPrimary,
            Boolean isActive,
            @DecimalMin("-99999999999.99") @DecimalMax("99999999999.99") BigDecimal openingBalance) {
    }

    public record AccountResponse(UUID id, String accountName, String accountType, String accountNumberMasked,
                                  String bankName, boolean isPrimary, boolean isActive, String currencyCode,
                                  BigDecimal openingBalance, Instant createdAt, Instant updatedAt) {
        public static AccountResponse from(Account a) {
            return new AccountResponse(a.getId(), a.getAccountName(), a.getAccountType(), a.getAccountNumberMasked(),
                    a.getBankName(), a.isPrimary(), a.isActive(), a.getCurrencyCode(), a.getOpeningBalance(),
                    Times.utc(a.getCreatedAt()), Times.utc(a.getUpdatedAt()));
        }
    }
}

package com.spendos.transactions.domain;

import com.spendos.categories.domain.Category;
import com.spendos.categories.domain.Subcategory;
import com.spendos.common.entity.BaseEntity;
import com.spendos.merchants.domain.Merchant;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Set;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * A single money movement. Amounts are always positive; direction is given by {@code transactionType}
 * (debit = money out, credit = money in, transfer = between the user's own accounts).
 * Foreign keys are plain UUID columns; the read-only associations exist for joins, sorting and display.
 */
@Entity
@Table(name = "transactions")
@Getter
@Setter
@NoArgsConstructor
public class Transaction extends BaseEntity {

    public static final String DEBIT = "debit";
    public static final String CREDIT = "credit";
    public static final String TRANSFER = "transfer";
    public static final Set<String> TYPES = Set.of(DEBIT, CREDIT, TRANSFER);
    public static final Set<String> PAYMENT_METHODS = Set.of("upi", "card", "net_banking", "cash", "wallet");

    public static final String SOURCE_RULE = "rule";
    public static final String SOURCE_MERCHANT_MAPPING = "merchant_mapping";
    public static final String SOURCE_USER = "user";

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "account_id", nullable = false)
    private UUID accountId;

    @Column(name = "merchant_id")
    private UUID merchantId;

    @Column(name = "category_id")
    private UUID categoryId;

    @Column(name = "subcategory_id")
    private UUID subcategoryId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "merchant_id", insertable = false, updatable = false)
    private Merchant merchant;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id", insertable = false, updatable = false)
    private Category category;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "subcategory_id", insertable = false, updatable = false)
    private Subcategory subcategory;

    @Column(name = "amount", nullable = false, precision = 19, scale = 2)
    private BigDecimal amount;

    @Column(name = "currency_code", length = 3)
    private String currencyCode = "INR";

    @Column(name = "transaction_type", nullable = false, length = 50)
    private String transactionType;

    @Column(name = "transaction_date", nullable = false)
    private LocalDate transactionDate;

    @Column(name = "description")
    private String description;

    @Column(name = "raw_description")
    private String rawDescription;

    @Column(name = "payment_method", length = 50)
    private String paymentMethod;

    @Column(name = "external_reference", length = 255)
    private String externalReference;

    @Column(name = "is_recurring")
    private boolean recurring;

    @Column(name = "is_transfer")
    private boolean transfer;

    @Column(name = "transfer_to_account_id")
    private UUID transferToAccountId;

    @Column(name = "categorization_confidence", precision = 3, scale = 2)
    private BigDecimal categorizationConfidence;

    @Column(name = "categorization_source", length = 50)
    private String categorizationSource;

    @Column(name = "is_duplicate")
    private boolean duplicate;

    @Column(name = "duplicate_of_id")
    private UUID duplicateOfId;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    public boolean isDebit() {
        return DEBIT.equals(transactionType);
    }

    public boolean isCredit() {
        return CREDIT.equals(transactionType);
    }
}

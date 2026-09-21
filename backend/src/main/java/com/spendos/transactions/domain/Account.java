package com.spendos.transactions.domain;

import com.spendos.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.util.Set;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "accounts")
@Getter
@Setter
@NoArgsConstructor
public class Account extends BaseEntity {

    public static final Set<String> TYPES = Set.of("savings", "checking", "credit", "digital_wallet");

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "account_name", nullable = false, length = 255)
    private String accountName;

    @Column(name = "account_type", nullable = false, length = 50)
    private String accountType;

    /** Last four digits only; full account numbers are never stored. */
    @Column(name = "account_number_masked", length = 20)
    private String accountNumberMasked;

    @Column(name = "bank_name", length = 255)
    private String bankName;

    @Column(name = "is_primary")
    private boolean primary;

    @Column(name = "is_active")
    private boolean active = true;

    @Column(name = "currency_code", length = 3)
    private String currencyCode = "INR";

    @Column(name = "opening_balance", precision = 19, scale = 2)
    private BigDecimal openingBalance = BigDecimal.ZERO;
}

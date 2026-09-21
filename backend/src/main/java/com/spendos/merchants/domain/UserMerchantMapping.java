package com.spendos.merchants.domain;

import com.spendos.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** A user's correction: raw merchant text maps to a canonical merchant and (optionally) a category. */
@Entity
@Table(name = "user_merchant_mappings")
@Getter
@Setter
@NoArgsConstructor
public class UserMerchantMapping extends BaseEntity {

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "raw_merchant_name", nullable = false, length = 255)
    private String rawMerchantName;

    @Column(name = "normalized_merchant_id", nullable = false)
    private UUID normalizedMerchantId;

    @Column(name = "category_id")
    private UUID categoryId;
}

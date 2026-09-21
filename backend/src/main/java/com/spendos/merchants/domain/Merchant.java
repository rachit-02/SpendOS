package com.spendos.merchants.domain;

import com.spendos.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.util.Locale;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Canonical merchant shared by all users (e.g. "Zomato"). User-specific corrections live in mappings. */
@Entity
@Table(name = "merchants")
@Getter
@Setter
@NoArgsConstructor
public class Merchant extends BaseEntity {

    @Column(name = "merchant_name", nullable = false, unique = true, length = 255)
    private String merchantName;

    @Column(name = "merchant_name_lower", nullable = false, unique = true, length = 255)
    private String merchantNameLower;

    @Column(name = "logo_url", length = 500)
    private String logoUrl;

    @Column(name = "website", length = 500)
    private String website;

    @Column(name = "category_id")
    private UUID categoryId;

    @Column(name = "confidence_score", precision = 3, scale = 2)
    private BigDecimal confidenceScore;

    @Column(name = "is_verified")
    private boolean verified;

    @Column(name = "is_active")
    private boolean active = true;

    public Merchant(String merchantName, UUID categoryId) {
        setName(merchantName);
        this.categoryId = categoryId;
    }

    public void setName(String merchantName) {
        this.merchantName = merchantName;
        this.merchantNameLower = merchantName.toLowerCase(Locale.ROOT);
    }
}

package com.spendos.merchants.domain;

import com.spendos.common.entity.CreatedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Global rule mapping raw statement text to a canonical merchant. Lower priority value wins. */
@Entity
@Table(name = "merchant_normalization_rules")
@Getter
@Setter
@NoArgsConstructor
public class MerchantNormalizationRule extends CreatedEntity {

    public static final String EXACT = "exact";
    public static final String CONTAINS = "contains";
    public static final String REGEX = "regex";
    public static final String STARTS_WITH = "starts_with";

    @Column(name = "pattern", nullable = false, length = 255)
    private String pattern;

    @Column(name = "pattern_type", nullable = false, length = 50)
    private String patternType;

    @Column(name = "target_merchant_id", nullable = false)
    private UUID targetMerchantId;

    @Column(name = "priority")
    private Integer priority = 100;

    @Column(name = "is_active")
    private boolean active = true;
}

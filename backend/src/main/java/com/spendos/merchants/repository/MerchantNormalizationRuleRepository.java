package com.spendos.merchants.repository;

import com.spendos.common.repository.BaseRepository;
import com.spendos.merchants.domain.MerchantNormalizationRule;
import java.util.List;

public interface MerchantNormalizationRuleRepository extends BaseRepository<MerchantNormalizationRule> {
    List<MerchantNormalizationRule> findByActiveTrueOrderByPriorityAsc();
}

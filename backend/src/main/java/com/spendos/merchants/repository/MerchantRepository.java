package com.spendos.merchants.repository;

import com.spendos.common.repository.BaseRepository;
import com.spendos.merchants.domain.Merchant;
import java.util.Optional;

public interface MerchantRepository extends BaseRepository<Merchant> {
    Optional<Merchant> findByMerchantNameLower(String merchantNameLower);
}

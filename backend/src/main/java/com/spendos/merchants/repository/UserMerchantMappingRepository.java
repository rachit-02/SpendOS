package com.spendos.merchants.repository;

import com.spendos.common.repository.BaseRepository;
import com.spendos.merchants.domain.UserMerchantMapping;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface UserMerchantMappingRepository extends BaseRepository<UserMerchantMapping> {
    List<UserMerchantMapping> findByUserId(UUID userId);

    Page<UserMerchantMapping> findByUserId(UUID userId, Pageable pageable);

    Optional<UserMerchantMapping> findByUserIdAndRawMerchantNameIgnoreCase(UUID userId, String rawMerchantName);
}

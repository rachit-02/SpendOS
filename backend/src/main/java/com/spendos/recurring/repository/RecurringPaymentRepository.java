package com.spendos.recurring.repository;

import com.spendos.common.repository.BaseRepository;
import com.spendos.recurring.domain.RecurringPayment;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RecurringPaymentRepository extends BaseRepository<RecurringPayment> {
    List<RecurringPayment> findByUserId(UUID userId);

    List<RecurringPayment> findByUserIdAndActiveTrue(UUID userId);

    Optional<RecurringPayment> findByIdAndUserId(UUID id, UUID userId);
}

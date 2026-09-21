package com.spendos.simulations.repository;

import com.spendos.common.repository.BaseRepository;
import com.spendos.simulations.domain.Simulation;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SimulationRepository extends BaseRepository<Simulation> {
    List<Simulation> findByUserIdOrderByCreatedAtDesc(UUID userId);

    Optional<Simulation> findByIdAndUserId(UUID id, UUID userId);

    List<Simulation> findByUserIdAndIdIn(UUID userId, Collection<UUID> ids);
}

package com.spendos.users.repository;

import com.spendos.common.repository.BaseRepository;
import com.spendos.users.domain.UserPreferences;
import java.util.Optional;
import java.util.UUID;

public interface UserPreferencesRepository extends BaseRepository<UserPreferences> {
    Optional<UserPreferences> findByUserId(UUID userId);
}

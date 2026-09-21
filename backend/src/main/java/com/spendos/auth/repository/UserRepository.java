package com.spendos.auth.repository;

import com.spendos.auth.entity.User;
import com.spendos.common.repository.BaseRepository;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Repository;

@Repository
public interface UserRepository extends BaseRepository<User> {
    boolean existsByEmailIgnoreCase(String email);

    Optional<User> findByEmailIgnoreCaseAndDeletedAtIsNull(String email);

    Optional<User> findByIdAndDeletedAtIsNull(UUID id);
}

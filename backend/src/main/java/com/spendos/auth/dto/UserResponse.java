package com.spendos.auth.dto;

import com.spendos.auth.entity.User;
import com.spendos.common.util.Times;
import java.time.Instant;
import java.util.UUID;

public record UserResponse(UUID userId, String email, String fullName, boolean emailVerified,
                           Instant createdAt, Instant updatedAt) {
    public static UserResponse from(User user) {
        return new UserResponse(user.getId(), user.getEmail(), user.getFullName(), user.isEmailVerified(),
                Times.utc(user.getCreatedAt()), Times.utc(user.getUpdatedAt()));
    }
}

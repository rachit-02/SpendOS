package com.spendos.auth.dto;

import com.spendos.auth.entity.User;
import java.time.LocalDateTime;
import java.util.UUID;

public record UserResponse(UUID userId, String email, String fullName, LocalDateTime createdAt) {
    public static UserResponse from(User user) {
        return new UserResponse(user.getId(), user.getEmail(), user.getFullName(), user.getCreatedAt());
    }
}

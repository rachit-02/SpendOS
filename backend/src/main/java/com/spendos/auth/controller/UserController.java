package com.spendos.auth.controller;

import com.spendos.auth.dto.UserResponse;
import com.spendos.auth.entity.User;
import com.spendos.auth.repository.UserRepository;
import com.spendos.common.dto.ApiResponse;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1/users")
public class UserController {

    private final UserRepository userRepository;

    public UserController(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @GetMapping("/me")
    public ResponseEntity<ApiResponse<UserResponse>> currentUser(@AuthenticationPrincipal UUID userId) {
        UserResponse response = userRepository.findByIdAndDeletedAtIsNull(userId)
                .filter(User::isActive)
                .map(UserResponse::from)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));
        return ResponseEntity.ok(ApiResponse.success(response));
    }
}

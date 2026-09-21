package com.spendos.users.controller;

import com.spendos.auth.dto.UserResponse;
import com.spendos.common.dto.ApiResponse;
import com.spendos.users.dto.UserDtos.ChangePasswordRequest;
import com.spendos.users.dto.UserDtos.DeleteAccountRequest;
import com.spendos.users.dto.UserDtos.MessageResponse;
import com.spendos.users.dto.UserDtos.PreferencesResponse;
import com.spendos.users.dto.UserDtos.UpdatePreferencesRequest;
import com.spendos.users.dto.UserDtos.UpdateProfileRequest;
import com.spendos.users.service.UserPreferencesService;
import com.spendos.users.service.UserService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Endpoints act only on the authenticated user ("me"); there is no way to address another user. */
@RestController
@RequestMapping("/v1/users/me")
@Tag(name = "Users")
public class UserController {

    private final UserService userService;
    private final UserPreferencesService preferencesService;

    public UserController(UserService userService, UserPreferencesService preferencesService) {
        this.userService = userService;
        this.preferencesService = preferencesService;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<UserResponse>> currentUser(@AuthenticationPrincipal UUID userId) {
        return ResponseEntity.ok(ApiResponse.success(userService.getCurrentUser(userId)));
    }

    @PutMapping
    public ResponseEntity<ApiResponse<UserResponse>> updateProfile(
            @AuthenticationPrincipal UUID userId, @Valid @RequestBody UpdateProfileRequest request) {
        return ResponseEntity.ok(ApiResponse.success(userService.updateProfile(userId, request)));
    }

    @PostMapping("/change-password")
    public ResponseEntity<ApiResponse<MessageResponse>> changePassword(
            @AuthenticationPrincipal UUID userId, @Valid @RequestBody ChangePasswordRequest request) {
        userService.changePassword(userId, request);
        return ResponseEntity.ok(ApiResponse.success(new MessageResponse("Password changed successfully")));
    }

    @DeleteMapping
    public ResponseEntity<ApiResponse<MessageResponse>> deleteAccount(
            @AuthenticationPrincipal UUID userId, @Valid @RequestBody DeleteAccountRequest request) {
        userService.deleteAccount(userId, request.confirmPassword());
        return ResponseEntity.ok(ApiResponse.success(new MessageResponse(
                "Account deleted successfully. Your data will be permanently removed within 30 days.")));
    }

    @GetMapping("/preferences")
    public ResponseEntity<ApiResponse<PreferencesResponse>> preferences(@AuthenticationPrincipal UUID userId) {
        return ResponseEntity.ok(ApiResponse.success(preferencesService.get(userId)));
    }

    @PutMapping("/preferences")
    public ResponseEntity<ApiResponse<PreferencesResponse>> updatePreferences(
            @AuthenticationPrincipal UUID userId, @Valid @RequestBody UpdatePreferencesRequest request) {
        return ResponseEntity.ok(ApiResponse.success(preferencesService.update(userId, request)));
    }
}

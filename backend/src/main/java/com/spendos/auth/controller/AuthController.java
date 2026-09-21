package com.spendos.auth.controller;

import com.spendos.auth.dto.AuthResponse;
import com.spendos.auth.dto.LoginRequest;
import com.spendos.auth.dto.LogoutRequest;
import com.spendos.auth.dto.RefreshRequest;
import com.spendos.auth.dto.RefreshResponse;
import com.spendos.auth.dto.RegisterRequest;
import com.spendos.auth.dto.UserResponse;
import com.spendos.auth.service.AuthService;
import com.spendos.common.dto.ApiResponse;
import com.spendos.common.security.JwtAuthenticationFilter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1/auth")
@Tag(name = "Authentication")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/register")
    public ResponseEntity<ApiResponse<UserResponse>> register(@Valid @RequestBody RegisterRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(authService.register(request)));
    }

    @PostMapping("/login")
    public ResponseEntity<ApiResponse<AuthResponse>> login(@Valid @RequestBody LoginRequest request) {
        return ResponseEntity.ok(ApiResponse.success(authService.login(request)));
    }

    @PostMapping("/refresh")
    public ResponseEntity<ApiResponse<RefreshResponse>> refresh(@Valid @RequestBody RefreshRequest request) {
        return ResponseEntity.ok(ApiResponse.success(authService.refresh(request)));
    }

    @PostMapping("/logout")
    public ResponseEntity<ApiResponse<Map<String, String>>> logout(
            HttpServletRequest httpRequest, @RequestBody(required = false) LogoutRequest request) {
        authService.logout(JwtAuthenticationFilter.bearerToken(httpRequest), request);
        return ResponseEntity.ok(ApiResponse.success(Map.of("message", "Logged out successfully")));
    }
}

package com.spendos.demo.controller;

import com.spendos.auth.dto.AuthResponse;
import com.spendos.common.dto.ApiResponse;
import com.spendos.common.exception.ApiException;
import com.spendos.demo.service.DemoService;
import com.spendos.users.dto.UserDtos.MessageResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

/** Demo mode (PRODUCT_SPEC.md 17): try every feature on synthetic data, and reset it at any time. */
@RestController
@Tag(name = "Demo")
public class DemoController {

    private final DemoService demoService;
    private final boolean enabled;

    public DemoController(DemoService demoService, @Value("${features.demo-mode-enabled:true}") boolean enabled) {
        this.demoService = demoService;
        this.enabled = enabled;
    }

    /** Public (under /v1/auth): creates a demo account and signs it in. Rate-limited per IP. */
    @PostMapping("/v1/auth/demo")
    public ResponseEntity<ApiResponse<AuthResponse>> start() {
        requireEnabled();
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(demoService.createDemoAccount()));
    }

    @PostMapping("/v1/demo/reset")
    public ResponseEntity<ApiResponse<MessageResponse>> reset(@AuthenticationPrincipal UUID userId) {
        requireEnabled();
        demoService.reset(userId);
        return ResponseEntity.ok(ApiResponse.success(new MessageResponse("Demo data has been reset.")));
    }

    private void requireEnabled() {
        if (!enabled) {
            throw new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "Demo mode is not available");
        }
    }
}

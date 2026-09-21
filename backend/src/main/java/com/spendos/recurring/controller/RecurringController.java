package com.spendos.recurring.controller;

import com.spendos.common.dto.ApiResponse;
import com.spendos.recurring.dto.RecurringDtos.DecisionResponse;
import com.spendos.recurring.dto.RecurringDtos.DetectionSummary;
import com.spendos.recurring.dto.RecurringDtos.RecurringResponse;
import com.spendos.recurring.service.RecurringService;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1/recurring")
@Tag(name = "Recurring payments")
public class RecurringController {

    private final RecurringService recurringService;

    public RecurringController(RecurringService recurringService) {
        this.recurringService = recurringService;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<RecurringResponse>>> list(
            @AuthenticationPrincipal UUID userId,
            @RequestParam(defaultValue = "all") String confirmed,
            @RequestParam(defaultValue = "nextDate") String sortBy) {
        return ResponseEntity.ok(ApiResponse.success(recurringService.list(userId, confirmed, sortBy)));
    }

    @GetMapping("/upcoming")
    public ResponseEntity<ApiResponse<List<RecurringResponse>>> upcoming(
            @AuthenticationPrincipal UUID userId, @RequestParam(defaultValue = "30") int days) {
        return ResponseEntity.ok(ApiResponse.success(recurringService.upcoming(userId, Math.max(1, Math.min(days, 366)))));
    }

    @PostMapping("/detect")
    public ResponseEntity<ApiResponse<DetectionSummary>> detect(@AuthenticationPrincipal UUID userId) {
        return ResponseEntity.ok(ApiResponse.success(recurringService.detect(userId)));
    }

    @PostMapping("/{recurringId}/confirm")
    public ResponseEntity<ApiResponse<DecisionResponse>> confirm(
            @AuthenticationPrincipal UUID userId, @PathVariable UUID recurringId) {
        return ResponseEntity.ok(ApiResponse.success(recurringService.confirm(userId, recurringId)));
    }

    @PostMapping("/{recurringId}/reject")
    public ResponseEntity<ApiResponse<DecisionResponse>> reject(
            @AuthenticationPrincipal UUID userId, @PathVariable UUID recurringId) {
        return ResponseEntity.ok(ApiResponse.success(recurringService.reject(userId, recurringId)));
    }
}

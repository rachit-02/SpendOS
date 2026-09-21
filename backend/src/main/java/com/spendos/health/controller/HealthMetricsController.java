package com.spendos.health.controller;

import com.spendos.common.dto.ApiResponse;
import com.spendos.health.dto.HealthMetricsDtos.Explanation;
import com.spendos.health.dto.HealthMetricsDtos.HealthMetrics;
import com.spendos.health.dto.HealthMetricsDtos.HistoryPoint;
import com.spendos.health.service.HealthMetricsService;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1/health-metrics")
@Tag(name = "Financial health")
public class HealthMetricsController {

    private final HealthMetricsService healthMetricsService;

    public HealthMetricsController(HealthMetricsService healthMetricsService) {
        this.healthMetricsService = healthMetricsService;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<HealthMetrics>> current(@AuthenticationPrincipal UUID userId) {
        return ResponseEntity.ok(ApiResponse.success(healthMetricsService.current(userId)));
    }

    @GetMapping("/history")
    public ResponseEntity<ApiResponse<List<HistoryPoint>>> history(
            @AuthenticationPrincipal UUID userId, @RequestParam(defaultValue = "6") int months) {
        return ResponseEntity.ok(ApiResponse.success(healthMetricsService.history(userId, months)));
    }

    @GetMapping("/explanation")
    public ResponseEntity<ApiResponse<Explanation>> explanation(
            @AuthenticationPrincipal UUID userId,
            @RequestParam(required = false) Integer month,
            @RequestParam(required = false) Integer year) {
        return ResponseEntity.ok(ApiResponse.success(healthMetricsService.explanation(userId, month, year)));
    }
}

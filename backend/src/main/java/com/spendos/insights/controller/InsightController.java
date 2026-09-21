package com.spendos.insights.controller;

import com.spendos.common.dto.ApiResponse;
import com.spendos.insights.domain.Insight;
import com.spendos.insights.dto.InsightDtos.AnomalyResponse;
import com.spendos.insights.dto.InsightDtos.InsightResponse;
import com.spendos.insights.service.InsightService;
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
@RequestMapping("/v1/insights")
@Tag(name = "Insights")
public class InsightController {

    private final InsightService insightService;

    public InsightController(InsightService insightService) {
        this.insightService = insightService;
    }

    /** All insights for a period, most important first. {@code period}: current_month, last_month or YYYY-MM. */
    @GetMapping
    public ResponseEntity<ApiResponse<List<InsightResponse>>> list(
            @AuthenticationPrincipal UUID userId,
            @RequestParam(defaultValue = "all") String type,
            @RequestParam(defaultValue = "current_month") String period) {
        return ResponseEntity.ok(ApiResponse.success(insightService.list(userId, type, period)));
    }

    @GetMapping("/anomalies")
    public ResponseEntity<ApiResponse<List<AnomalyResponse>>> anomalies(
            @AuthenticationPrincipal UUID userId,
            @RequestParam(required = false) UUID categoryId,
            @RequestParam(defaultValue = "medium") String sensitivityLevel) {
        return ResponseEntity.ok(ApiResponse.success(insightService.anomalies(userId, categoryId, sensitivityLevel)));
    }

    @GetMapping("/leaks")
    public ResponseEntity<ApiResponse<List<InsightResponse>>> leaks(
            @AuthenticationPrincipal UUID userId, @RequestParam(defaultValue = "current_month") String period) {
        return ResponseEntity.ok(ApiResponse.success(insightService.list(userId, Insight.MONEY_LEAK, period)));
    }

    @GetMapping("/opportunities")
    public ResponseEntity<ApiResponse<List<InsightResponse>>> opportunities(
            @AuthenticationPrincipal UUID userId, @RequestParam(defaultValue = "current_month") String period) {
        return ResponseEntity.ok(ApiResponse.success(insightService.list(userId, Insight.OPPORTUNITY, period)));
    }

    @GetMapping("/history")
    public ResponseEntity<ApiResponse<List<InsightResponse>>> history(
            @AuthenticationPrincipal UUID userId, @RequestParam(defaultValue = "6") int months) {
        return ResponseEntity.ok(ApiResponse.success(insightService.history(userId, months)));
    }

    @PostMapping("/generate")
    public ResponseEntity<ApiResponse<List<InsightResponse>>> generate(
            @AuthenticationPrincipal UUID userId, @RequestParam(defaultValue = "current_month") String period) {
        return ResponseEntity.ok(ApiResponse.success(insightService.regenerate(userId, period)));
    }

    /** One insight with the transactions it is based on. */
    @GetMapping("/{insightId}")
    public ResponseEntity<ApiResponse<InsightResponse>> get(
            @AuthenticationPrincipal UUID userId, @PathVariable UUID insightId) {
        return ResponseEntity.ok(ApiResponse.success(insightService.get(userId, insightId)));
    }
}

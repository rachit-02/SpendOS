package com.spendos.analytics.controller;

import com.spendos.analytics.dto.AnalyticsDtos.CategoryStatistics;
import com.spendos.analytics.dto.AnalyticsDtos.CategoryTrend;
import com.spendos.analytics.dto.AnalyticsDtos.MerchantStat;
import com.spendos.analytics.dto.AnalyticsDtos.MonthComparison;
import com.spendos.analytics.dto.AnalyticsDtos.MonthlyAnalytics;
import com.spendos.analytics.dto.AnalyticsDtos.Trends;
import com.spendos.analytics.service.AnalyticsExportService;
import com.spendos.analytics.service.AnalyticsService;
import com.spendos.analytics.service.PeriodResolver;
import com.spendos.common.dto.ApiResponse;
import com.spendos.common.exception.ApiException;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.UUID;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Tag(name = "Analytics")
public class AnalyticsController {

    private final AnalyticsService analyticsService;
    private final AnalyticsExportService exportService;
    private final PeriodResolver periods;

    public AnalyticsController(AnalyticsService analyticsService, AnalyticsExportService exportService,
                               PeriodResolver periods) {
        this.analyticsService = analyticsService;
        this.exportService = exportService;
        this.periods = periods;
    }

    @GetMapping("/v1/analytics/monthly")
    public ResponseEntity<ApiResponse<MonthlyAnalytics>> monthly(
            @AuthenticationPrincipal UUID userId,
            @RequestParam(required = false) Integer month,
            @RequestParam(required = false) Integer year) {
        return ResponseEntity.ok(ApiResponse.success(analyticsService.monthly(userId, month, year)));
    }

    @GetMapping("/v1/analytics/monthly/export")
    public ResponseEntity<byte[]> exportMonthly(
            @AuthenticationPrincipal UUID userId,
            @RequestParam(required = false) Integer month,
            @RequestParam(required = false) Integer year,
            @RequestParam(defaultValue = "csv") String format) {
        YearMonth period = periods.resolve(userId, month, year);
        String baseName = "spendos-analytics-" + period;
        return switch (format.toLowerCase()) {
            case "csv" -> download(exportService.monthlyCsv(userId, month, year), baseName + ".csv",
                    new MediaType("text", "csv"));
            case "pdf" -> download(exportService.monthlyPdf(userId, month, year), baseName + ".pdf",
                    MediaType.APPLICATION_PDF);
            default -> throw ApiException.badRequest("INVALID_REQUEST", "format must be csv or pdf");
        };
    }

    @GetMapping("/v1/analytics/categories/trends")
    public ResponseEntity<ApiResponse<CategoryTrend>> categoryTrend(
            @AuthenticationPrincipal UUID userId,
            @RequestParam UUID categoryId,
            @RequestParam(defaultValue = "6") int months) {
        return ResponseEntity.ok(ApiResponse.success(analyticsService.categoryTrend(userId, categoryId, months)));
    }

    @GetMapping("/v1/analytics/merchants/top")
    public ResponseEntity<ApiResponse<List<MerchantStat>>> topMerchants(
            @AuthenticationPrincipal UUID userId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(required = false) UUID categoryId,
            @RequestParam(defaultValue = "10") int limit) {
        return ResponseEntity.ok(ApiResponse.success(
                analyticsService.topMerchants(userId, startDate, endDate, categoryId, limit)));
    }

    @GetMapping("/v1/analytics/trends")
    public ResponseEntity<ApiResponse<Trends>> trends(
            @AuthenticationPrincipal UUID userId, @RequestParam(defaultValue = "12") int months) {
        return ResponseEntity.ok(ApiResponse.success(analyticsService.trends(userId, months)));
    }

    @GetMapping("/v1/analytics/compare")
    public ResponseEntity<ApiResponse<MonthComparison>> compare(
            @AuthenticationPrincipal UUID userId,
            @RequestParam int month1, @RequestParam int year1,
            @RequestParam int month2, @RequestParam int year2) {
        return ResponseEntity.ok(ApiResponse.success(analyticsService.compare(userId,
                periods.resolve(userId, month1, year1), periods.resolve(userId, month2, year2))));
    }

    @GetMapping("/v1/categories/{categoryId}/statistics")
    public ResponseEntity<ApiResponse<CategoryStatistics>> categoryStatistics(
            @AuthenticationPrincipal UUID userId,
            @PathVariable UUID categoryId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        return ResponseEntity.ok(ApiResponse.success(
                analyticsService.categoryStatistics(userId, categoryId, startDate, endDate)));
    }

    private static ResponseEntity<byte[]> download(byte[] body, String fileName, MediaType type) {
        return ResponseEntity.ok()
                .contentType(type)
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(fileName).build().toString())
                .body(body);
    }
}

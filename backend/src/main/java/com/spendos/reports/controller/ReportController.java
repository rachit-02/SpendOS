package com.spendos.reports.controller;

import com.spendos.analytics.service.PeriodResolver;
import com.spendos.audit.service.AuditService;
import com.spendos.common.dto.ApiResponse;
import com.spendos.reports.dto.AutopsyDtos.MonthlyAutopsy;
import com.spendos.reports.dto.AutopsyDtos.ReportSummary;
import com.spendos.reports.service.AutopsyPdfRenderer;
import com.spendos.reports.service.AutopsyService;
import com.spendos.reports.dto.PredictionDtos.Affordability;
import com.spendos.reports.dto.PredictionDtos.AffordabilityRequest;
import com.spendos.reports.dto.PredictionDtos.SpendingPrediction;
import com.spendos.reports.service.PredictionService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.RequestBody;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1/reports")
@Tag(name = "Reports")
public class ReportController {

    private final AutopsyService autopsyService;
    private final AutopsyPdfRenderer pdfRenderer;
    private final PeriodResolver periods;
    private final AuditService auditService;
    private final PredictionService predictionService;

    public ReportController(AutopsyService autopsyService, AutopsyPdfRenderer pdfRenderer, PeriodResolver periods,
                            AuditService auditService, PredictionService predictionService) {
        this.predictionService = predictionService;
        this.autopsyService = autopsyService;
        this.pdfRenderer = pdfRenderer;
        this.periods = periods;
        this.auditService = auditService;
    }

    @GetMapping("/monthly-autopsy")
    public ResponseEntity<ApiResponse<MonthlyAutopsy>> autopsy(
            @AuthenticationPrincipal UUID userId,
            @RequestParam(required = false) Integer month,
            @RequestParam(required = false) Integer year) {
        return ResponseEntity.ok(ApiResponse.success(autopsyService.get(userId, month, year)));
    }

    @PostMapping("/generate-autopsy")
    public ResponseEntity<ApiResponse<MonthlyAutopsy>> generate(
            @AuthenticationPrincipal UUID userId,
            @RequestParam(required = false) Integer month,
            @RequestParam(required = false) Integer year) {
        return ResponseEntity.ok(ApiResponse.success(
                autopsyService.generate(userId, periods.resolve(userId, month, year))));
    }

    @GetMapping("/spending-prediction")
    public ResponseEntity<ApiResponse<SpendingPrediction>> prediction(
            @AuthenticationPrincipal UUID userId,
            @RequestParam(required = false) Integer month,
            @RequestParam(required = false) Integer year) {
        return ResponseEntity.ok(ApiResponse.success(predictionService.predict(userId, month, year)));
    }

    @PostMapping("/affordability")
    public ResponseEntity<ApiResponse<Affordability>> affordability(
            @AuthenticationPrincipal UUID userId, @Valid @RequestBody AffordabilityRequest request) {
        return ResponseEntity.ok(ApiResponse.success(predictionService.affordability(userId, request)));
    }

    @GetMapping("/autopsies")
    public ResponseEntity<ApiResponse<List<ReportSummary>>> list(@AuthenticationPrincipal UUID userId) {
        return ResponseEntity.ok(ApiResponse.success(autopsyService.list(userId)));
    }

    @GetMapping(value = "/monthly-autopsy/pdf", produces = MediaType.APPLICATION_PDF_VALUE)
    @Transactional
    public ResponseEntity<byte[]> autopsyPdf(
            @AuthenticationPrincipal UUID userId,
            @RequestParam(required = false) Integer month,
            @RequestParam(required = false) Integer year) {
        YearMonth period = periods.resolve(userId, month, year);
        MonthlyAutopsy report = autopsyService.get(userId, period.getMonthValue(), period.getYear());
        auditService.record(userId, "monthly_report", period.toString(), AuditService.EXPORT, null,
                Map.of("format", "pdf"));
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename("spendos-autopsy-" + period + ".pdf").build().toString())
                .body(pdfRenderer.render(report));
    }
}

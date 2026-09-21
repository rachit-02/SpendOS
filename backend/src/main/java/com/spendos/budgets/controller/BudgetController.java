package com.spendos.budgets.controller;

import com.spendos.budgets.dto.BudgetDtos.BudgetAlert;
import com.spendos.budgets.dto.BudgetDtos.BudgetProgress;
import com.spendos.budgets.dto.BudgetDtos.BudgetRequest;
import com.spendos.budgets.dto.BudgetDtos.BudgetResponse;
import com.spendos.budgets.service.BudgetService;
import com.spendos.common.dto.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1/budgets")
@Tag(name = "Budgets")
public class BudgetController {

    private final BudgetService budgetService;

    public BudgetController(BudgetService budgetService) {
        this.budgetService = budgetService;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<BudgetResponse>>> list(
            @AuthenticationPrincipal UUID userId, @RequestParam(required = false) Boolean active) {
        return ResponseEntity.ok(ApiResponse.success(budgetService.list(userId, active)));
    }

    @GetMapping("/alerts")
    public ResponseEntity<ApiResponse<List<BudgetAlert>>> alerts(@AuthenticationPrincipal UUID userId) {
        return ResponseEntity.ok(ApiResponse.success(budgetService.alerts(userId)));
    }

    @GetMapping("/{budgetId}")
    public ResponseEntity<ApiResponse<BudgetResponse>> get(@AuthenticationPrincipal UUID userId, @PathVariable UUID budgetId) {
        return ResponseEntity.ok(ApiResponse.success(budgetService.get(userId, budgetId)));
    }

    @GetMapping("/{budgetId}/progress")
    public ResponseEntity<ApiResponse<BudgetProgress>> progress(
            @AuthenticationPrincipal UUID userId, @PathVariable UUID budgetId) {
        return ResponseEntity.ok(ApiResponse.success(budgetService.progress(userId, budgetId)));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<BudgetResponse>> create(
            @AuthenticationPrincipal UUID userId, @Valid @RequestBody BudgetRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(budgetService.create(userId, request)));
    }

    @PutMapping("/{budgetId}")
    public ResponseEntity<ApiResponse<BudgetResponse>> update(
            @AuthenticationPrincipal UUID userId, @PathVariable UUID budgetId, @Valid @RequestBody BudgetRequest request) {
        return ResponseEntity.ok(ApiResponse.success(budgetService.update(userId, budgetId, request)));
    }

    @DeleteMapping("/{budgetId}")
    public ResponseEntity<Void> delete(@AuthenticationPrincipal UUID userId, @PathVariable UUID budgetId) {
        budgetService.delete(userId, budgetId);
        return ResponseEntity.noContent().build();
    }
}

package com.spendos.goals.controller;

import com.spendos.common.dto.ApiResponse;
import com.spendos.goals.dto.GoalDtos.ContributionRequest;
import com.spendos.goals.dto.GoalDtos.GoalRequest;
import com.spendos.goals.dto.GoalDtos.GoalResponse;
import com.spendos.goals.service.GoalService;
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
@RequestMapping("/v1/goals")
@Tag(name = "Goals")
public class GoalController {

    private final GoalService goalService;

    public GoalController(GoalService goalService) {
        this.goalService = goalService;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<GoalResponse>>> list(
            @AuthenticationPrincipal UUID userId,
            @RequestParam(required = false) Boolean active,
            @RequestParam(defaultValue = "targetDate") String sortBy) {
        return ResponseEntity.ok(ApiResponse.success(goalService.list(userId, active, sortBy)));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<GoalResponse>> create(
            @AuthenticationPrincipal UUID userId, @Valid @RequestBody GoalRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(goalService.create(userId, request)));
    }

    @PutMapping("/{goalId}")
    public ResponseEntity<ApiResponse<GoalResponse>> update(
            @AuthenticationPrincipal UUID userId, @PathVariable UUID goalId, @Valid @RequestBody GoalRequest request) {
        return ResponseEntity.ok(ApiResponse.success(goalService.update(userId, goalId, request)));
    }

    @PostMapping("/{goalId}/contributions")
    public ResponseEntity<ApiResponse<GoalResponse>> contribute(
            @AuthenticationPrincipal UUID userId, @PathVariable UUID goalId,
            @Valid @RequestBody ContributionRequest request) {
        return ResponseEntity.ok(ApiResponse.success(goalService.contribute(userId, goalId, request.amount())));
    }

    @DeleteMapping("/{goalId}")
    public ResponseEntity<Void> delete(@AuthenticationPrincipal UUID userId, @PathVariable UUID goalId) {
        goalService.delete(userId, goalId);
        return ResponseEntity.noContent().build();
    }
}

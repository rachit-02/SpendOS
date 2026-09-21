package com.spendos.simulations.controller;

import com.spendos.common.dto.ApiResponse;
import com.spendos.simulations.dto.SimulationDtos.Comparison;
import com.spendos.simulations.dto.SimulationDtos.SimulationRequest;
import com.spendos.simulations.dto.SimulationDtos.SimulationResponse;
import com.spendos.simulations.service.SimulationService;
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
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1/simulations")
@Tag(name = "What-if simulator")
public class SimulationController {

    private final SimulationService simulationService;

    public SimulationController(SimulationService simulationService) {
        this.simulationService = simulationService;
    }

    @PostMapping
    public ResponseEntity<ApiResponse<SimulationResponse>> create(
            @AuthenticationPrincipal UUID userId, @Valid @RequestBody SimulationRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(simulationService.create(userId, request)));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<SimulationResponse>>> list(@AuthenticationPrincipal UUID userId) {
        return ResponseEntity.ok(ApiResponse.success(simulationService.list(userId)));
    }

    @GetMapping("/compare")
    public ResponseEntity<ApiResponse<Comparison>> compare(
            @AuthenticationPrincipal UUID userId, @RequestParam List<UUID> ids) {
        return ResponseEntity.ok(ApiResponse.success(simulationService.compare(userId, ids)));
    }

    @GetMapping("/{simulationId}")
    public ResponseEntity<ApiResponse<SimulationResponse>> get(
            @AuthenticationPrincipal UUID userId, @PathVariable UUID simulationId) {
        return ResponseEntity.ok(ApiResponse.success(simulationService.get(userId, simulationId)));
    }

    @DeleteMapping("/{simulationId}")
    public ResponseEntity<Void> delete(@AuthenticationPrincipal UUID userId, @PathVariable UUID simulationId) {
        simulationService.delete(userId, simulationId);
        return ResponseEntity.noContent().build();
    }
}

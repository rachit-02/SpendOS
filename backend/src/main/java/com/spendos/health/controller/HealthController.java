package com.spendos.health.controller;

import com.spendos.common.dto.ApiResponse;
import com.spendos.common.health.DatabaseHealthIndicator;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.boot.actuate.health.Status;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Public health summary at {@code GET /api/v1/health}. Exposes component status only, never details. */
@RestController
@RequestMapping("/v1/health")
public class HealthController {

    private final DatabaseHealthIndicator databaseHealthIndicator;

    public HealthController(DatabaseHealthIndicator databaseHealthIndicator) {
        this.databaseHealthIndicator = databaseHealthIndicator;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<Map<String, Object>>> health() {
        Status database = databaseHealthIndicator.health().getStatus();
        boolean up = Status.UP.equals(database);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("status", up ? "UP" : "DOWN");
        body.put("timestamp", Instant.now());
        body.put("checks", Map.of("database", database.getCode()));
        return ResponseEntity.status(up ? HttpStatus.OK : HttpStatus.SERVICE_UNAVAILABLE)
                .body(ApiResponse.success(body));
    }
}

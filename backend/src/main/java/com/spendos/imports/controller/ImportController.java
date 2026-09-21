package com.spendos.imports.controller;

import com.spendos.common.dto.ApiResponse;
import com.spendos.imports.dto.ImportDtos.ImportErrorResponse;
import com.spendos.imports.dto.ImportDtos.ImportJobResponse;
import com.spendos.imports.dto.ImportDtos.ImportStatusResponse;
import com.spendos.imports.dto.ImportDtos.UploadResponse;
import com.spendos.imports.service.ImportService;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/v1/imports")
@Tag(name = "Imports")
public class ImportController {

    private final ImportService importService;

    public ImportController(ImportService importService) {
        this.importService = importService;
    }

    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<UploadResponse>> upload(
            @AuthenticationPrincipal UUID userId,
            @RequestPart("file") MultipartFile file,
            @RequestParam(required = false) UUID accountId,
            @RequestParam(required = false) String dateFormat) {
        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .body(ApiResponse.success(importService.upload(userId, file, accountId, dateFormat)));
    }

    @GetMapping("/history")
    public ResponseEntity<ApiResponse<List<ImportJobResponse>>> history(
            @AuthenticationPrincipal UUID userId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int pageSize,
            @RequestParam(defaultValue = "createdAt") String sortBy,
            @RequestParam(defaultValue = "desc") String sortOrder) {
        return ResponseEntity.ok(ApiResponse.page(importService.history(userId, page, pageSize, sortBy, sortOrder)));
    }

    @GetMapping("/{importJobId}")
    public ResponseEntity<ApiResponse<ImportJobResponse>> get(
            @AuthenticationPrincipal UUID userId, @PathVariable UUID importJobId) {
        return ResponseEntity.ok(ApiResponse.success(importService.get(userId, importJobId)));
    }

    @GetMapping("/{importJobId}/status")
    public ResponseEntity<ApiResponse<ImportStatusResponse>> status(
            @AuthenticationPrincipal UUID userId, @PathVariable UUID importJobId) {
        return ResponseEntity.ok(ApiResponse.success(importService.status(userId, importJobId)));
    }

    @GetMapping("/{importJobId}/errors")
    public ResponseEntity<ApiResponse<List<ImportErrorResponse>>> errors(
            @AuthenticationPrincipal UUID userId, @PathVariable UUID importJobId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize) {
        return ResponseEntity.ok(ApiResponse.page(importService.errors(userId, importJobId, page, pageSize)));
    }
}

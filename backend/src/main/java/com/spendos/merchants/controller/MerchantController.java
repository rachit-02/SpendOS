package com.spendos.merchants.controller;

import com.spendos.common.dto.ApiResponse;
import com.spendos.merchants.dto.MerchantDtos.CategoryUpdateRequest;
import com.spendos.merchants.dto.MerchantDtos.MappingRequest;
import com.spendos.merchants.dto.MerchantDtos.MappingResponse;
import com.spendos.merchants.dto.MerchantDtos.MerchantResponse;
import com.spendos.merchants.dto.MerchantDtos.MerchantSuggestion;
import com.spendos.merchants.service.MerchantCatalogService;
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
@RequestMapping("/v1/merchants")
@Tag(name = "Merchants")
public class MerchantController {

    private final MerchantCatalogService merchantService;

    public MerchantController(MerchantCatalogService merchantService) {
        this.merchantService = merchantService;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<MerchantResponse>>> list(
            @AuthenticationPrincipal UUID userId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "50") int pageSize,
            @RequestParam(required = false) String searchText,
            @RequestParam(required = false) UUID categoryId,
            @RequestParam(required = false) String sortBy) {
        return ResponseEntity.ok(ApiResponse.page(merchantService.list(userId, page, pageSize, searchText, categoryId, sortBy)));
    }

    @GetMapping("/mappings")
    public ResponseEntity<ApiResponse<List<MappingResponse>>> mappings(
            @AuthenticationPrincipal UUID userId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "50") int pageSize) {
        return ResponseEntity.ok(ApiResponse.page(merchantService.mappings(userId, page, pageSize)));
    }

    @PostMapping("/mappings")
    public ResponseEntity<ApiResponse<MappingResponse>> createMapping(
            @AuthenticationPrincipal UUID userId, @Valid @RequestBody MappingRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(merchantService.createMapping(userId, request)));
    }

    @DeleteMapping("/mappings/{mappingId}")
    public ResponseEntity<Void> deleteMapping(@AuthenticationPrincipal UUID userId, @PathVariable UUID mappingId) {
        merchantService.deleteMapping(userId, mappingId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/suggestions")
    public ResponseEntity<ApiResponse<List<MerchantSuggestion>>> suggestions(@AuthenticationPrincipal UUID userId) {
        return ResponseEntity.ok(ApiResponse.success(merchantService.suggestions(userId)));
    }

    @GetMapping("/{merchantId}")
    public ResponseEntity<ApiResponse<MerchantResponse>> get(@AuthenticationPrincipal UUID userId, @PathVariable UUID merchantId) {
        return ResponseEntity.ok(ApiResponse.success(merchantService.get(userId, merchantId)));
    }

    @PutMapping("/{merchantId}/category")
    public ResponseEntity<ApiResponse<MerchantResponse>> updateCategory(
            @AuthenticationPrincipal UUID userId, @PathVariable UUID merchantId,
            @Valid @RequestBody CategoryUpdateRequest request) {
        return ResponseEntity.ok(ApiResponse.success(merchantService.updateCategory(userId, merchantId, request.categoryId())));
    }
}

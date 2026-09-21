package com.spendos.transactions.controller;

import com.spendos.common.dto.ApiResponse;
import com.spendos.transactions.dto.TransactionDtos.BulkDeleteRequest;
import com.spendos.transactions.dto.TransactionDtos.BulkDeleteResult;
import com.spendos.transactions.dto.TransactionDtos.BulkResult;
import com.spendos.transactions.dto.TransactionDtos.BulkUpdateRequest;
import com.spendos.transactions.dto.TransactionDtos.CreateTransactionRequest;
import com.spendos.transactions.dto.TransactionDtos.TransactionResponse;
import com.spendos.transactions.dto.TransactionDtos.UpdateTransactionRequest;
import com.spendos.transactions.dto.TransactionFilter;
import com.spendos.transactions.service.TransactionExportService;
import com.spendos.transactions.service.TransactionService;
import com.spendos.transactions.service.TransactionSuggestionService;
import com.spendos.transactions.service.TransactionSuggestionService.Suggestions;
import java.nio.charset.StandardCharsets;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.format.annotation.DateTimeFormat;
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
@RequestMapping("/v1/transactions")
@Tag(name = "Transactions")
public class TransactionController {

    private final TransactionService transactionService;
    private final TransactionExportService exportService;
    private final TransactionSuggestionService suggestionService;

    public TransactionController(TransactionService transactionService, TransactionExportService exportService,
                                 TransactionSuggestionService suggestionService) {
        this.transactionService = transactionService;
        this.exportService = exportService;
        this.suggestionService = suggestionService;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<TransactionResponse>>> list(
            @AuthenticationPrincipal UUID userId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(required = false) UUID categoryId,
            @RequestParam(required = false) UUID merchantId,
            @RequestParam(required = false) UUID accountId,
            @RequestParam(required = false) BigDecimal minAmount,
            @RequestParam(required = false) BigDecimal maxAmount,
            @RequestParam(required = false) String transactionType,
            @RequestParam(required = false) String paymentMethod,
            @RequestParam(required = false) String searchText,
            @RequestParam(required = false) Boolean isRecurring,
            @RequestParam(defaultValue = "date") String sortBy,
            @RequestParam(defaultValue = "desc") String sortOrder) {
        TransactionFilter filter = new TransactionFilter(startDate, endDate, categoryId, merchantId, accountId,
                minAmount, maxAmount, transactionType, paymentMethod, searchText, isRecurring);
        return ResponseEntity.ok(ApiResponse.page(
                transactionService.list(userId, filter, page, pageSize, sortBy, sortOrder)));
    }

    /** CSV download of the filtered transactions, or of the given IDs when {@code ids} is present. */
    @GetMapping(value = "/export", produces = "text/csv")
    public ResponseEntity<byte[]> export(
            @AuthenticationPrincipal UUID userId,
            @RequestParam(required = false) List<UUID> ids,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(required = false) UUID categoryId,
            @RequestParam(required = false) UUID merchantId,
            @RequestParam(required = false) UUID accountId,
            @RequestParam(required = false) BigDecimal minAmount,
            @RequestParam(required = false) BigDecimal maxAmount,
            @RequestParam(required = false) String transactionType,
            @RequestParam(required = false) String paymentMethod,
            @RequestParam(required = false) String searchText) {
        TransactionFilter filter = new TransactionFilter(startDate, endDate, categoryId, merchantId, accountId,
                minAmount, maxAmount, transactionType, paymentMethod, searchText, null);
        byte[] csv = exportService.exportCsv(userId, filter, ids).getBytes(StandardCharsets.UTF_8);
        return ResponseEntity.ok()
                .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename("spendos-transactions-" + LocalDate.now() + ".csv").build().toString())
                .body(csv);
    }

    @GetMapping("/suggestions")
    public ResponseEntity<ApiResponse<Suggestions>> suggestions(
            @AuthenticationPrincipal UUID userId,
            @RequestParam(required = false, name = "q") String query,
            @RequestParam(defaultValue = "8") int limit) {
        return ResponseEntity.ok(ApiResponse.success(
                suggestionService.suggest(userId, query, Math.max(1, Math.min(limit, 20)))));
    }

    @GetMapping("/{transactionId}")
    public ResponseEntity<ApiResponse<TransactionResponse>> get(
            @AuthenticationPrincipal UUID userId, @PathVariable UUID transactionId) {
        return ResponseEntity.ok(ApiResponse.success(transactionService.get(userId, transactionId)));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<TransactionResponse>> create(
            @AuthenticationPrincipal UUID userId, @Valid @RequestBody CreateTransactionRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(transactionService.create(userId, request)));
    }

    @PutMapping("/{transactionId}")
    public ResponseEntity<ApiResponse<TransactionResponse>> update(
            @AuthenticationPrincipal UUID userId, @PathVariable UUID transactionId,
            @Valid @RequestBody UpdateTransactionRequest request) {
        return ResponseEntity.ok(ApiResponse.success(transactionService.update(userId, transactionId, request)));
    }

    @DeleteMapping("/{transactionId}")
    public ResponseEntity<Void> delete(@AuthenticationPrincipal UUID userId, @PathVariable UUID transactionId) {
        transactionService.delete(userId, transactionId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/bulk-update")
    public ResponseEntity<ApiResponse<BulkResult>> bulkUpdate(
            @AuthenticationPrincipal UUID userId, @Valid @RequestBody BulkUpdateRequest request) {
        return ResponseEntity.ok(ApiResponse.success(transactionService.bulkUpdate(userId, request)));
    }

    @PostMapping("/bulk-delete")
    public ResponseEntity<ApiResponse<BulkDeleteResult>> bulkDelete(
            @AuthenticationPrincipal UUID userId, @Valid @RequestBody BulkDeleteRequest request) {
        return ResponseEntity.ok(ApiResponse.success(transactionService.bulkDelete(userId, request.transactionIds())));
    }
}

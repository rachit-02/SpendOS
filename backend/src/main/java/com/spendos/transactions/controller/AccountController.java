package com.spendos.transactions.controller;

import com.spendos.common.dto.ApiResponse;
import com.spendos.transactions.dto.AccountDtos.AccountResponse;
import com.spendos.transactions.dto.AccountDtos.CreateAccountRequest;
import com.spendos.transactions.dto.AccountDtos.UpdateAccountRequest;
import com.spendos.transactions.service.AccountService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1/accounts")
@Tag(name = "Accounts")
public class AccountController {

    private final AccountService accountService;

    public AccountController(AccountService accountService) {
        this.accountService = accountService;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<AccountResponse>>> list(@AuthenticationPrincipal UUID userId) {
        return ResponseEntity.ok(ApiResponse.success(accountService.list(userId)));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<AccountResponse>> create(
            @AuthenticationPrincipal UUID userId, @Valid @RequestBody CreateAccountRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(accountService.create(userId, request)));
    }

    @PutMapping("/{accountId}")
    public ResponseEntity<ApiResponse<AccountResponse>> update(
            @AuthenticationPrincipal UUID userId, @PathVariable UUID accountId,
            @Valid @RequestBody UpdateAccountRequest request) {
        return ResponseEntity.ok(ApiResponse.success(accountService.update(userId, accountId, request)));
    }
}

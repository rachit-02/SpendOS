package com.spendos.transactions.service;

import com.spendos.audit.service.AuditService;
import com.spendos.common.exception.ApiException;
import com.spendos.transactions.domain.Account;
import com.spendos.transactions.dto.AccountDtos.AccountResponse;
import com.spendos.transactions.dto.AccountDtos.CreateAccountRequest;
import com.spendos.transactions.dto.AccountDtos.UpdateAccountRequest;
import com.spendos.transactions.repository.AccountRepository;
import com.spendos.users.service.UserPreferencesService;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AccountService {

    private final AccountRepository accountRepository;
    private final UserPreferencesService preferencesService;
    private final AuditService auditService;

    public AccountService(AccountRepository accountRepository, UserPreferencesService preferencesService,
                          AuditService auditService) {
        this.accountRepository = accountRepository;
        this.preferencesService = preferencesService;
        this.auditService = auditService;
    }

    @Transactional(readOnly = true)
    public List<AccountResponse> list(UUID userId) {
        return accountRepository.findByUserIdOrderByPrimaryDescCreatedAtAsc(userId).stream()
                .map(AccountResponse::from).toList();
    }

    /** The user's first account becomes primary automatically. */
    @Transactional
    public AccountResponse create(UUID userId, CreateAccountRequest request) {
        boolean first = accountRepository.countByUserId(userId) == 0;
        boolean primary = first || Boolean.TRUE.equals(request.isPrimary());
        if (primary && !first) {
            accountRepository.clearPrimary(userId);
        }
        Account account = new Account();
        account.setUserId(userId);
        account.setAccountName(request.accountName().trim());
        account.setAccountType(request.accountType());
        account.setAccountNumberMasked(mask(request.accountNumberLast4()));
        account.setBankName(request.bankName());
        account.setPrimary(primary);
        account.setCurrencyCode(request.currencyCode() != null
                ? request.currencyCode() : preferencesService.currencyFor(userId));
        if (request.openingBalance() != null) {
            account.setOpeningBalance(request.openingBalance());
        }
        Account saved = accountRepository.save(account);
        auditService.record(userId, "account", saved.getId(), AuditService.CREATE, null,
                Map.of("accountName", saved.getAccountName(), "accountType", saved.getAccountType()));
        return AccountResponse.from(saved);
    }

    @Transactional
    public AccountResponse update(UUID userId, UUID accountId, UpdateAccountRequest request) {
        Account account = require(userId, accountId);
        if (Boolean.TRUE.equals(request.isPrimary()) && !account.isPrimary()) {
            accountRepository.clearPrimary(userId);
            account = require(userId, accountId);
            account.setPrimary(true);
        }
        if (request.accountName() != null) {
            account.setAccountName(request.accountName().trim());
        }
        if (request.accountType() != null) {
            account.setAccountType(request.accountType());
        }
        if (request.accountNumberLast4() != null) {
            account.setAccountNumberMasked(mask(request.accountNumberLast4()));
        }
        if (request.bankName() != null) {
            account.setBankName(request.bankName());
        }
        if (request.isActive() != null) {
            account.setActive(request.isActive());
        }
        if (request.openingBalance() != null) {
            account.setOpeningBalance(request.openingBalance());
        }
        Account saved = accountRepository.save(account);
        auditService.record(userId, "account", accountId, AuditService.UPDATE, null,
                Map.of("accountName", saved.getAccountName(), "isActive", saved.isActive()));
        return AccountResponse.from(saved);
    }

    /** Loads an account owned by the user; other users' accounts are indistinguishable from missing ones. */
    @Transactional(readOnly = true)
    public Account require(UUID userId, UUID accountId) {
        return accountRepository.findByIdAndUserId(accountId, userId)
                .orElseThrow(() -> ApiException.notFound("Account not found"));
    }

    /** Returns the user's primary account, creating a default one if the user has none. */
    @Transactional
    public Account primaryOrDefault(UUID userId) {
        return accountRepository.findByUserIdOrderByPrimaryDescCreatedAtAsc(userId).stream().findFirst()
                .orElseGet(() -> {
                    Account account = new Account();
                    account.setUserId(userId);
                    account.setAccountName("Main account");
                    account.setAccountType("savings");
                    account.setPrimary(true);
                    account.setCurrencyCode(preferencesService.currencyFor(userId));
                    return accountRepository.save(account);
                });
    }

    private static String mask(String last4) {
        return last4 == null ? null : "XXXX" + last4;
    }
}

package com.spendos.transactions.service;

import com.spendos.audit.service.AuditService;
import com.spendos.categories.domain.Category;
import com.spendos.categories.service.CategoryService;
import com.spendos.common.exception.ApiException;
import com.spendos.common.util.PageRequests;
import com.spendos.merchants.domain.Merchant;
import com.spendos.merchants.service.MerchantService;
import com.spendos.transactions.domain.Transaction;
import com.spendos.transactions.dto.TransactionDtos.BulkDeleteResult;
import com.spendos.transactions.dto.TransactionDtos.BulkResult;
import com.spendos.transactions.dto.TransactionDtos.BulkUpdateRequest;
import com.spendos.transactions.dto.TransactionDtos.CreateTransactionRequest;
import com.spendos.transactions.dto.TransactionDtos.TransactionResponse;
import com.spendos.transactions.dto.TransactionDtos.UpdateTransactionRequest;
import com.spendos.transactions.dto.TransactionFilter;
import com.spendos.transactions.repository.TransactionRepository;
import com.spendos.transactions.repository.TransactionSpecifications;
import com.spendos.users.service.UserPreferencesService;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TransactionService {

    public static final LocalDate EARLIEST_DATE = LocalDate.of(1990, 1, 1);
    private static final Map<String, String> SORT_FIELDS = Map.of(
            "date", "transactionDate",
            "amount", "amount",
            "merchant", "merchant.merchantName",
            "createdAt", "createdAt");

    private final TransactionRepository transactionRepository;
    private final AccountService accountService;
    private final CategoryService categoryService;
    private final MerchantService merchantService;
    private final UserPreferencesService preferencesService;
    private final AuditService auditService;
    private final ApplicationEventPublisher events;
    private final EntityManager entityManager;

    public TransactionService(TransactionRepository transactionRepository, AccountService accountService,
                              CategoryService categoryService, MerchantService merchantService,
                              UserPreferencesService preferencesService, AuditService auditService,
                              ApplicationEventPublisher events, EntityManager entityManager) {
        this.transactionRepository = transactionRepository;
        this.accountService = accountService;
        this.categoryService = categoryService;
        this.merchantService = merchantService;
        this.preferencesService = preferencesService;
        this.auditService = auditService;
        this.events = events;
        this.entityManager = entityManager;
    }

    @Transactional(readOnly = true)
    public Page<TransactionResponse> list(UUID userId, TransactionFilter filter, int page, int pageSize,
                                          String sortBy, String sortOrder) {
        validateFilter(filter);
        String field = SORT_FIELDS.get(sortBy == null || sortBy.isBlank() ? "date" : sortBy);
        if (field == null) {
            throw ApiException.badRequest("INVALID_REQUEST", "sortBy must be one of date, amount, merchant");
        }
        Sort.Direction direction = PageRequests.direction(sortOrder);
        Sort sort = Sort.by(direction, field).and(Sort.by(direction, "createdAt")).and(Sort.by(direction, "id"));
        return transactionRepository.findAll(TransactionSpecifications.forUser(userId, filter),
                        PageRequests.of(page, pageSize, sort))
                .map(TransactionResponse::from);
    }

    @Transactional(readOnly = true)
    public TransactionResponse get(UUID userId, UUID transactionId) {
        return TransactionResponse.from(require(userId, transactionId));
    }

    @Transactional
    public TransactionResponse create(UUID userId, CreateTransactionRequest request) {
        accountService.require(userId, request.accountId());
        validateDate(userId, request.transactionDate());

        Transaction transaction = new Transaction();
        transaction.setUserId(userId);
        transaction.setAccountId(request.accountId());
        transaction.setAmount(request.amount());
        transaction.setCurrencyCode(request.currencyCode() != null
                ? request.currencyCode() : preferencesService.currencyFor(userId));
        transaction.setTransactionType(request.transactionType());
        transaction.setTransfer(Transaction.TRANSFER.equals(request.transactionType()));
        transaction.setTransactionDate(request.transactionDate());
        transaction.setDescription(blankToNull(request.description()));
        transaction.setRawDescription(MerchantService.clean(request.merchantName()));
        transaction.setPaymentMethod(request.paymentMethod());
        transaction.setExternalReference(blankToNull(request.externalReference()));

        // New global merchants start uncategorized: one user's choice must not categorize other users' data.
        MerchantService.Resolved resolved = merchantService.resolve(userId, request.merchantName());
        Merchant merchant = resolved.merchant();
        transaction.setMerchantId(merchant.getId());
        applyCategory(transaction, request.categoryId(), request.subcategoryId(), merchant, resolved.mappedCategoryId());

        Transaction saved = transactionRepository.saveAndFlush(transaction);
        auditService.record(userId, "transaction", saved.getId(), AuditService.CREATE, null, snapshot(saved));
        events.publishEvent(new TransactionsChangedEvent(userId));
        entityManager.refresh(saved); // load merchant/category associations for the response
        return TransactionResponse.from(saved);
    }

    @Transactional
    public TransactionResponse update(UUID userId, UUID transactionId, UpdateTransactionRequest request) {
        Transaction transaction = require(userId, transactionId);
        Map<String, Object> before = snapshot(transaction);

        if (request.accountId() != null) {
            accountService.require(userId, request.accountId());
            transaction.setAccountId(request.accountId());
        }
        if (request.amount() != null) {
            transaction.setAmount(request.amount());
        }
        if (request.transactionType() != null) {
            transaction.setTransactionType(request.transactionType());
            transaction.setTransfer(Transaction.TRANSFER.equals(request.transactionType()));
        }
        if (request.transactionDate() != null) {
            validateDate(userId, request.transactionDate());
            transaction.setTransactionDate(request.transactionDate());
        }
        if (request.description() != null) {
            transaction.setDescription(blankToNull(request.description()));
        }
        if (request.paymentMethod() != null) {
            transaction.setPaymentMethod(request.paymentMethod());
        }
        if (request.isRecurring() != null) {
            transaction.setRecurring(request.isRecurring());
        }
        if (request.merchantName() != null) {
            Merchant merchant = merchantService.resolve(userId, request.merchantName()).merchant();
            transaction.setMerchantId(merchant.getId());
            transaction.setMerchant(merchant);
        }
        if (request.categoryId() != null) {
            applyCategory(transaction, request.categoryId(), request.subcategoryId(), null, null);
        } else if (request.subcategoryId() != null) {
            applyCategory(transaction, transaction.getCategoryId(), request.subcategoryId(), null, null);
        }

        Transaction saved = transactionRepository.saveAndFlush(transaction);
        auditService.record(userId, "transaction", transactionId, AuditService.UPDATE, before, snapshot(saved));
        events.publishEvent(new TransactionsChangedEvent(userId));
        entityManager.refresh(saved);
        return TransactionResponse.from(saved);
    }

    @Transactional
    public void delete(UUID userId, UUID transactionId) {
        Transaction transaction = require(userId, transactionId);
        transactionRepository.clearDuplicateReferences(List.of(transactionId));
        transactionRepository.deleteById(transaction.getId());
        auditService.record(userId, "transaction", transactionId, AuditService.DELETE, snapshot(transaction), null);
        events.publishEvent(new TransactionsChangedEvent(userId));
    }

    /** Applies the same change to many transactions; IDs the user does not own count as failed. */
    @Transactional
    public BulkResult bulkUpdate(UUID userId, BulkUpdateRequest request) {
        var updates = request.updates();
        if (updates.categoryId() == null && updates.subcategoryId() == null
                && updates.paymentMethod() == null && updates.isRecurring() == null) {
            throw ApiException.badRequest("INVALID_REQUEST", "No updates supplied");
        }
        if (updates.categoryId() != null) {
            categoryService.require(updates.categoryId());
            if (updates.subcategoryId() != null) {
                categoryService.requireSubcategoryOf(updates.subcategoryId(), updates.categoryId());
            }
        }
        Set<UUID> requested = Set.copyOf(request.transactionIds());
        List<Transaction> owned = transactionRepository.findByUserIdAndIdIn(userId, requested);
        for (Transaction transaction : owned) {
            Map<String, Object> before = snapshot(transaction);
            if (updates.categoryId() != null) {
                applyCategory(transaction, updates.categoryId(), updates.subcategoryId(), null, null);
            }
            if (updates.paymentMethod() != null) {
                transaction.setPaymentMethod(updates.paymentMethod());
            }
            if (updates.isRecurring() != null) {
                transaction.setRecurring(updates.isRecurring());
            }
            auditService.record(userId, "transaction", transaction.getId(), AuditService.UPDATE, before,
                    snapshot(transaction));
        }
        transactionRepository.saveAll(owned);
        if (!owned.isEmpty()) {
            events.publishEvent(new TransactionsChangedEvent(userId));
        }
        return new BulkResult(owned.size(), requested.size() - owned.size());
    }

    @Transactional
    public BulkDeleteResult bulkDelete(UUID userId, List<UUID> transactionIds) {
        Set<UUID> requested = Set.copyOf(transactionIds);
        List<Transaction> owned = transactionRepository.findByUserIdAndIdIn(userId, requested);
        if (!owned.isEmpty()) {
            List<UUID> ids = owned.stream().map(Transaction::getId).toList();
            transactionRepository.clearDuplicateReferences(ids);
            owned.forEach(t -> auditService.record(userId, "transaction", t.getId(), AuditService.DELETE,
                    snapshot(t), null));
            transactionRepository.deleteAllByIdInBatch(ids);
            events.publishEvent(new TransactionsChangedEvent(userId));
        }
        return new BulkDeleteResult(owned.size(), requested.size() - owned.size());
    }

    /** Loads a transaction owned by the user; anything else is reported as not found. */
    @Transactional(readOnly = true)
    public Transaction require(UUID userId, UUID transactionId) {
        return transactionRepository.findByIdAndUserId(transactionId, userId)
                .filter(t -> t.getDeletedAt() == null)
                .orElseThrow(() -> ApiException.notFound("Transaction not found"));
    }

    /** User-chosen category wins; otherwise the merchant's category; otherwise "Other"/"Income". */
    private void applyCategory(Transaction transaction, UUID categoryId, UUID subcategoryId, Merchant merchant,
                               UUID mappedCategoryId) {
        if (categoryId != null) {
            Category category = categoryService.require(categoryId);
            if (subcategoryId != null) {
                categoryService.requireSubcategoryOf(subcategoryId, categoryId);
            }
            transaction.setCategoryId(category.getId());
            transaction.setCategory(category);
            transaction.setSubcategoryId(subcategoryId);
            transaction.setCategorizationSource(Transaction.SOURCE_USER);
            transaction.setCategorizationConfidence(BigDecimal.ONE);
            return;
        }
        if (mappedCategoryId != null) {
            // The user's own correction for this merchant.
            transaction.setCategoryId(mappedCategoryId);
            transaction.setCategorizationSource(Transaction.SOURCE_MERCHANT_MAPPING);
            transaction.setCategorizationConfidence(BigDecimal.ONE);
            return;
        }
        if (merchant != null && merchant.getCategoryId() != null) {
            transaction.setCategoryId(merchant.getCategoryId());
            transaction.setCategorizationSource(Transaction.SOURCE_MERCHANT_MAPPING);
            transaction.setCategorizationConfidence(new BigDecimal("0.80"));
            return;
        }
        String fallback = transaction.isCredit() ? Category.INCOME : Category.OTHER;
        Category category = categoryService.findByName(fallback).orElseGet(categoryService::other);
        transaction.setCategoryId(category.getId());
        transaction.setCategorizationSource(Transaction.SOURCE_RULE);
        transaction.setCategorizationConfidence(new BigDecimal("0.30"));
    }

    private void validateDate(UUID userId, LocalDate date) {
        LocalDate today = LocalDate.now(preferencesService.zoneFor(userId));
        if (date.isAfter(today)) {
            throw ApiException.badRequest("INVALID_TRANSACTION", "Transaction date cannot be in the future");
        }
        if (date.isBefore(EARLIEST_DATE)) {
            throw ApiException.badRequest("INVALID_TRANSACTION", "Transaction date is too far in the past");
        }
    }

    private static void validateFilter(TransactionFilter filter) {
        if (filter.startDate() != null && filter.endDate() != null && filter.startDate().isAfter(filter.endDate())) {
            throw ApiException.badRequest("INVALID_REQUEST", "startDate must not be after endDate");
        }
        if (filter.minAmount() != null && filter.maxAmount() != null
                && filter.minAmount().compareTo(filter.maxAmount()) > 0) {
            throw ApiException.badRequest("INVALID_REQUEST", "minAmount must not exceed maxAmount");
        }
        if (filter.transactionType() != null && !Transaction.TYPES.contains(filter.transactionType())) {
            throw ApiException.badRequest("INVALID_REQUEST", "transactionType must be debit, credit or transfer");
        }
    }

    private static Map<String, Object> snapshot(Transaction t) {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("amount", t.getAmount() == null ? null : t.getAmount().toPlainString());
        values.put("transactionType", t.getTransactionType());
        values.put("transactionDate", String.valueOf(t.getTransactionDate()));
        values.put("categoryId", t.getCategoryId() == null ? null : t.getCategoryId().toString());
        values.put("merchantId", t.getMerchantId() == null ? null : t.getMerchantId().toString());
        values.put("accountId", t.getAccountId() == null ? null : t.getAccountId().toString());
        return values;
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}

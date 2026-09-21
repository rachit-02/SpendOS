package com.spendos.merchants.service;

import com.spendos.audit.service.AuditService;
import com.spendos.categories.domain.Category;
import com.spendos.categories.service.CategoryService;
import com.spendos.common.exception.ApiException;
import com.spendos.common.util.PageRequests;
import com.spendos.merchants.domain.Merchant;
import com.spendos.merchants.domain.UserMerchantMapping;
import com.spendos.merchants.dto.MerchantDtos.MappingRequest;
import com.spendos.merchants.dto.MerchantDtos.MappingResponse;
import com.spendos.merchants.dto.MerchantDtos.MerchantResponse;
import com.spendos.merchants.dto.MerchantDtos.MerchantSuggestion;
import com.spendos.merchants.normalizer.Similarity;
import com.spendos.merchants.repository.MerchantQueries;
import com.spendos.merchants.repository.MerchantQueries.Sort;
import com.spendos.merchants.repository.MerchantRepository;
import com.spendos.merchants.repository.UserMerchantMappingRepository;
import com.spendos.transactions.service.TransactionsChangedEvent;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Merchant directory and the user's corrections. Corrections are always per user: a mapping or a
 * category change rewrites only that user's transactions and is applied to their future imports and
 * manual entries; the shared merchant record is never modified by a user.
 */
@Service
public class MerchantCatalogService {

    static final double SUGGESTION_THRESHOLD = 0.6;
    private static final int MAX_SUGGESTIONS = 20;

    private final MerchantQueries queries;
    private final MerchantRepository merchantRepository;
    private final UserMerchantMappingRepository mappingRepository;
    private final CategoryService categoryService;
    private final AuditService auditService;
    private final ApplicationEventPublisher events;

    public MerchantCatalogService(MerchantQueries queries, MerchantRepository merchantRepository,
                                  UserMerchantMappingRepository mappingRepository, CategoryService categoryService,
                                  AuditService auditService, ApplicationEventPublisher events) {
        this.queries = queries;
        this.merchantRepository = merchantRepository;
        this.mappingRepository = mappingRepository;
        this.categoryService = categoryService;
        this.auditService = auditService;
        this.events = events;
    }

    @Transactional(readOnly = true)
    public Page<MerchantResponse> list(UUID userId, int page, int pageSize, String searchText, UUID categoryId, String sortBy) {
        PageRequest request = PageRequests.of(page, pageSize, org.springframework.data.domain.Sort.unsorted());
        Sort sort = switch (sortBy == null ? "name" : sortBy) {
            case "name" -> Sort.NAME;
            case "transactionCount" -> Sort.TRANSACTIONS;
            case "lastTransaction" -> Sort.LAST_TRANSACTION;
            default -> throw ApiException.badRequest("INVALID_REQUEST", "sortBy must be name, transactionCount or lastTransaction");
        };
        long total = queries.count(userId, searchText, categoryId);
        List<MerchantResponse> items = queries.list(userId, searchText, categoryId, sort, request.getPageSize(), request.getOffset());
        return new PageImpl<>(items, request, total);
    }

    @Transactional(readOnly = true)
    public MerchantResponse get(UUID userId, UUID merchantId) {
        return queries.find(userId, merchantId).orElseThrow(() -> ApiException.notFound("Merchant not found"));
    }

    /** Creates or replaces the user's mapping for this raw name and applies it to existing transactions. */
    @Transactional
    public MappingResponse createMapping(UUID userId, MappingRequest request) {
        String raw = MerchantService.clean(request.rawMerchantName());
        MerchantResponse target = get(userId, request.normalizedMerchantId());
        Category category = request.categoryId() == null ? null : categoryService.require(request.categoryId());

        UserMerchantMapping mapping = mappingRepository.findByUserIdAndRawMerchantNameIgnoreCase(userId, raw)
                .orElseGet(UserMerchantMapping::new);
        boolean created = mapping.getId() == null;
        Map<String, Object> before = created ? null : snapshot(mapping);
        mapping.setUserId(userId);
        mapping.setRawMerchantName(raw);
        mapping.setNormalizedMerchantId(target.id());
        mapping.setCategoryId(category == null ? null : category.getId());
        UserMerchantMapping saved = mappingRepository.saveAndFlush(mapping);

        int applied = queries.remapTransactions(userId, raw, target.id());
        if (category != null) {
            applied = Math.max(applied, queries.recategorizeTransactions(userId, target.id(), category.getId()));
        }
        auditService.record(userId, "merchant_mapping", saved.getId(), created ? AuditService.CREATE : AuditService.UPDATE,
                before, snapshot(saved));
        if (applied > 0) {
            events.publishEvent(new TransactionsChangedEvent(userId));
        }
        return new MappingResponse(saved.getId(), saved.getRawMerchantName(), target.id(), target.merchantName(),
                category == null ? null : category.getId(), category == null ? null : category.getCategoryName(),
                queries.find(userId, target.id()).map(MerchantResponse::transactionCount).orElse(0L), applied,
                com.spendos.common.util.Times.utc(saved.getCreatedAt()), com.spendos.common.util.Times.utc(saved.getUpdatedAt()));
    }

    /** "This merchant is always X for me": a mapping from the merchant's own name to itself. */
    @Transactional
    public MerchantResponse updateCategory(UUID userId, UUID merchantId, UUID categoryId) {
        MerchantResponse merchant = get(userId, merchantId);
        createMapping(userId, new MappingRequest(merchant.merchantName(), merchantId, categoryId));
        return get(userId, merchantId);
    }

    @Transactional(readOnly = true)
    public Page<MappingResponse> mappings(UUID userId, int page, int pageSize) {
        PageRequest request = PageRequests.of(page, pageSize, org.springframework.data.domain.Sort.unsorted());
        return new PageImpl<>(queries.mappings(userId, request.getPageSize(), request.getOffset()), request,
                queries.mappingCount(userId));
    }

    /** Removes a correction. Transactions already updated keep their merchant and category. */
    @Transactional
    public void deleteMapping(UUID userId, UUID mappingId) {
        UserMerchantMapping mapping = mappingRepository.findById(mappingId)
                .filter(m -> m.getUserId().equals(userId))
                .orElseThrow(() -> ApiException.notFound("Mapping not found"));
        auditService.record(userId, "merchant_mapping", mapping.getId(), AuditService.DELETE, snapshot(mapping), null);
        mappingRepository.delete(mapping);
    }

    /**
     * Unrecognised merchants in the user's data that look like a known merchant (Levenshtein
     * similarity of at least 0.6 on the name or one of its words), most used first.
     */
    @Transactional(readOnly = true)
    public List<MerchantSuggestion> suggestions(UUID userId) {
        List<Merchant> known = merchantRepository.findAll().stream().filter(Merchant::isVerified).filter(Merchant::isActive).toList();
        Set<String> alreadyMapped = mappingRepository.findByUserId(userId).stream()
                .map(m -> m.getRawMerchantName().toLowerCase(Locale.ROOT)).collect(Collectors.toSet());
        Map<UUID, String> categoryNames = new HashMap<>();
        categoryService.listCategories().forEach(c -> categoryNames.put(c.id(), c.categoryName()));

        Map<UUID, MerchantSuggestion> best = new LinkedHashMap<>();
        for (var mine : queries.unverifiedMerchants(userId)) {
            if (alreadyMapped.contains(mine.name().toLowerCase(Locale.ROOT))) {
                continue;
            }
            Merchant match = null;
            double score = 0;
            for (Merchant candidate : known) {
                double s = Similarity.score(mine.name(), candidate.getMerchantName());
                if (s > score) {
                    score = s;
                    match = candidate;
                }
            }
            if (match != null && score >= SUGGESTION_THRESHOLD && !Objects.equals(match.getId(), mine.id())) {
                best.put(mine.id(), new MerchantSuggestion(mine.id(), mine.name(), mine.transactionCount(), match.getId(),
                        match.getMerchantName(), match.getCategoryId(), categoryNames.get(match.getCategoryId()),
                        BigDecimal.valueOf(score).setScale(2, RoundingMode.HALF_UP)));
            }
        }
        return best.values().stream()
                .sorted(Comparator.comparingLong(MerchantSuggestion::transactionCount).reversed()
                        .thenComparing(MerchantSuggestion::similarity, Comparator.reverseOrder()))
                .limit(MAX_SUGGESTIONS)
                .toList();
    }

    private static Map<String, Object> snapshot(UserMerchantMapping mapping) {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("rawMerchantName", mapping.getRawMerchantName());
        values.put("normalizedMerchantId", mapping.getNormalizedMerchantId());
        values.put("categoryId", mapping.getCategoryId());
        return values;
    }
}

package com.spendos.insights.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.spendos.analytics.service.PeriodResolver;
import com.spendos.categories.service.CategoryService;
import com.spendos.common.exception.ApiException;
import com.spendos.common.util.Times;
import com.spendos.insights.detectors.AnomalyDetector;
import com.spendos.insights.detectors.InsightCandidate;
import com.spendos.insights.detectors.SpendingContext;
import com.spendos.insights.domain.Insight;
import com.spendos.insights.dto.InsightDtos.AnomalyResponse;
import com.spendos.insights.dto.InsightDtos.InsightResponse;
import com.spendos.insights.dto.InsightDtos.NormalRange;
import com.spendos.insights.dto.InsightDtos.RelatedTransaction;
import com.spendos.insights.engine.InsightEngine;
import com.spendos.insights.repository.InsightRepository;
import com.spendos.merchants.repository.MerchantRepository;
import com.spendos.transactions.domain.Transaction;
import com.spendos.transactions.repository.TransactionRepository;
import com.spendos.transactions.service.TransactionsChangedEvent;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Serves stored insights, regenerating them when the user's data changed since they were computed
 * (or when they are over a day old for the current month).
 */
@Service
public class InsightService {

    static final Set<String> TYPES = Set.of(Insight.ANOMALY, Insight.MONEY_LEAK, Insight.OPPORTUNITY, Insight.SPENDING_TREND);

    private final InsightRepository insightRepository;
    private final InsightEngine engine;
    private final TransactionRepository transactionRepository;
    private final CategoryService categoryService;
    private final MerchantRepository merchantRepository;
    private final PeriodResolver periods;
    private final ObjectMapper objectMapper;
    private final Map<UUID, Instant> lastDataChange = new ConcurrentHashMap<>();

    public InsightService(InsightRepository insightRepository, InsightEngine engine,
                          TransactionRepository transactionRepository, CategoryService categoryService,
                          MerchantRepository merchantRepository, PeriodResolver periods, ObjectMapper objectMapper) {
        this.insightRepository = insightRepository;
        this.engine = engine;
        this.transactionRepository = transactionRepository;
        this.categoryService = categoryService;
        this.merchantRepository = merchantRepository;
        this.periods = periods;
        this.objectMapper = objectMapper;
    }

    @EventListener
    public void onTransactionsChanged(TransactionsChangedEvent event) {
        lastDataChange.put(event.userId(), Instant.now());
    }

    @Transactional
    public List<InsightResponse> list(UUID userId, String type, String period) {
        if (type != null && !"all".equals(type) && !TYPES.contains(type)) {
            throw ApiException.badRequest("INVALID_REQUEST", "type must be all, " + String.join(", ", TYPES));
        }
        YearMonth month = resolvePeriod(userId, period);
        return fresh(userId, month).stream()
                .filter(i -> type == null || "all".equals(type) || type.equals(i.getInsightType()))
                .map(i -> toResponse(i, false))
                .toList();
    }

    @Transactional
    public List<InsightResponse> regenerate(UUID userId, String period) {
        YearMonth month = resolvePeriod(userId, period);
        return engine.generate(userId, month).stream().map(i -> toResponse(i, false)).toList();
    }

    @Transactional(readOnly = true)
    public InsightResponse get(UUID userId, UUID insightId) {
        Insight insight = insightRepository.findByIdAndUserId(insightId, userId)
                .orElseThrow(() -> ApiException.notFound("Insight not found"));
        return toResponse(insight, true);
    }

    /** Insights from earlier months, newest first. */
    @Transactional(readOnly = true)
    public List<InsightResponse> history(UUID userId, int months) {
        LocalDate currentStart = YearMonth.from(periods.today(userId)).atDay(1);
        LocalDate from = currentStart.minusMonths(Math.max(1, Math.min(months, 24)));
        return insightRepository.findByUserIdOrderByCreatedAtDesc(userId).stream()
                .filter(i -> i.getPeriodStartDate().isBefore(currentStart) && !i.getPeriodStartDate().isBefore(from))
                .sorted(Comparator.comparing(Insight::getPeriodStartDate).reversed()
                        .thenComparing(Insight::getImportanceScore, Comparator.reverseOrder()))
                .map(i -> toResponse(i, false))
                .toList();
    }

    /** Anomalies computed live so the caller can choose sensitivity (not stored). */
    @Transactional(readOnly = true)
    public List<AnomalyResponse> anomalies(UUID userId, UUID categoryId, String sensitivityLevel) {
        AnomalyDetector.Sensitivity sensitivity;
        try {
            sensitivity = AnomalyDetector.Sensitivity.valueOf(
                    (sensitivityLevel == null ? "medium" : sensitivityLevel).toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw ApiException.badRequest("INVALID_REQUEST", "sensitivityLevel must be low, medium or high");
        }
        SpendingContext context = engine.buildContext(userId, YearMonth.from(periods.today(userId)));
        return new AnomalyDetector(sensitivity).detect(context).stream()
                .filter(c -> categoryId == null || categoryId.equals(c.categoryId()))
                .map(candidate -> toAnomaly(userId, candidate))
                .toList();
    }

    /**
     * Current insights of the month, regenerating if the data changed since they were stored. Runs in its
     * own transaction because callers such as the dashboard are read-only.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public List<Insight> fresh(UUID userId, YearMonth month) {
        List<Insight> stored = insightRepository.findByUserIdOrderByCreatedAtDesc(userId).stream()
                .filter(i -> i.getPeriodStartDate().equals(month.atDay(1)))
                .toList();
        Instant changed = lastDataChange.get(userId);
        Instant generatedAt = stored.stream().map(i -> Times.utc(i.getCreatedAt())).max(Comparator.naturalOrder())
                .orElse(null);
        boolean currentMonth = month.equals(YearMonth.from(periods.today(userId)));
        boolean stale = generatedAt == null
                || (changed != null && changed.isAfter(generatedAt))
                || (currentMonth && generatedAt.isBefore(Instant.now().minusSeconds(24 * 3600)));
        List<Insight> result = stale ? engine.generate(userId, month) : stored;
        return result.stream().sorted(Comparator.comparing(Insight::getImportanceScore).reversed()).toList();
    }

    @Transactional
    public int purgeExpired() {
        return insightRepository.deleteExpired(LocalDateTime.now(ZoneOffset.UTC));
    }

    private YearMonth resolvePeriod(UUID userId, String period) {
        YearMonth current = YearMonth.from(periods.today(userId));
        if (period == null || period.isBlank() || "current_month".equals(period)) {
            return current;
        }
        if ("last_month".equals(period)) {
            return current.minusMonths(1);
        }
        try {
            YearMonth parsed = YearMonth.parse(period);
            if (parsed.isAfter(current)) {
                throw ApiException.badRequest("INVALID_REQUEST", "period cannot be in the future");
            }
            return parsed;
        } catch (DateTimeParseException exception) {
            throw ApiException.badRequest("INVALID_REQUEST", "period must be current_month, last_month or YYYY-MM");
        }
    }

    private InsightResponse toResponse(Insight insight, boolean withTransactions) {
        Map<String, Object> details = readDetails(insight.getDetails());
        List<RelatedTransaction> related = withTransactions ? related(insight.getUserId(), details) : null;
        details.remove("transactionIds");
        String categoryName = insight.getRelatedCategoryId() == null ? null
                : categoryService.listCategories().stream().filter(c -> c.id().equals(insight.getRelatedCategoryId()))
                .map(c -> c.categoryName()).findFirst().orElse(null);
        String merchantName = insight.getRelatedMerchantId() == null ? null
                : merchantRepository.findById(insight.getRelatedMerchantId()).map(m -> m.getMerchantName()).orElse(null);
        return new InsightResponse(insight.getId(), insight.getInsightType(), insight.getTitle(), insight.getDescription(),
                insight.getImpactValue(), insight.getImpactPercentage(), insight.getRelatedCategoryId(), categoryName,
                insight.getRelatedMerchantId(), merchantName, insight.isActionable(), insight.getSuggestedAction(),
                insight.getConfidence(), insight.getImportanceScore(), insight.getPeriodStartDate(),
                insight.getPeriodEndDate(), Times.utc(insight.getCreatedAt()), details, related);
    }

    private List<RelatedTransaction> related(UUID userId, Map<String, Object> details) {
        Object raw = details.get("transactionIds");
        if (!(raw instanceof List<?> ids) || ids.isEmpty()) {
            return List.of();
        }
        List<UUID> uuids = ids.stream().map(String::valueOf).map(UUID::fromString).limit(100).toList();
        Map<UUID, Integer> order = new LinkedHashMap<>();
        for (int i = 0; i < uuids.size(); i++) {
            order.put(uuids.get(i), i);
        }
        return transactionRepository.findByUserIdAndIdIn(userId, uuids).stream()
                .sorted(Comparator.comparing(t -> order.getOrDefault(t.getId(), Integer.MAX_VALUE)))
                .map(InsightService::relatedOf)
                .toList();
    }

    private AnomalyResponse toAnomaly(UUID userId, InsightCandidate candidate) {
        Map<String, Object> details = candidate.details();
        NormalRange range = null;
        if (details.get("normalRange") instanceof Map<?, ?> normal) {
            range = new NormalRange((BigDecimal) normal.get("min"), (BigDecimal) normal.get("max"));
        }
        List<RelatedTransaction> related = candidate.transactionIds().isEmpty() ? List.of() : relatedFor(userId, candidate);
        return new AnomalyResponse(String.valueOf(details.get("kind")), candidate.categoryId(),
                String.valueOf(details.get("categoryName")), range,
                details.get("currentAmount") instanceof BigDecimal amount ? amount : candidate.impactValue(),
                details.get("percentageAboveNormal") instanceof BigDecimal pct ? pct : candidate.impactPercentage(),
                "current_month", candidate.transactionIds().size(), candidate.description(), related);
    }

    private List<RelatedTransaction> relatedFor(UUID userId, InsightCandidate candidate) {
        return transactionRepository.findByUserIdAndIdIn(userId, candidate.transactionIds().stream().limit(50).toList())
                .stream()
                .sorted(Comparator.comparing(Transaction::getAmount).reversed())
                .map(InsightService::relatedOf)
                .collect(Collectors.toList());
    }

    private static RelatedTransaction relatedOf(Transaction t) {
        return new RelatedTransaction(t.getId(), t.getAmount(),
                t.getMerchant() != null ? t.getMerchant().getMerchantName() : t.getRawDescription(),
                t.getCategory() != null ? t.getCategory().getCategoryName() : null, t.getTransactionDate());
    }

    private Map<String, Object> readDetails(String json) {
        if (json == null || json.isBlank()) {
            return new LinkedHashMap<>();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<LinkedHashMap<String, Object>>() {
            });
        } catch (Exception exception) {
            return new LinkedHashMap<>();
        }
    }
}

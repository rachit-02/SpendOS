package com.spendos.insights.engine;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.spendos.analytics.repository.AggregateQueries;
import com.spendos.analytics.service.PeriodResolver;
import com.spendos.categories.domain.Category;
import com.spendos.insights.detectors.AnomalyDetector;
import com.spendos.insights.detectors.InsightCandidate;
import com.spendos.insights.detectors.InsightDetector;
import com.spendos.insights.detectors.MoneyLeakDetector;
import com.spendos.insights.detectors.OpportunityDetector;
import com.spendos.insights.detectors.SpendingContext;
import com.spendos.insights.detectors.SpendingContext.Tx;
import com.spendos.insights.detectors.SpendingTrendDetector;
import com.spendos.insights.domain.Insight;
import com.spendos.insights.repository.InsightRepository;
import com.spendos.recurring.domain.RecurringPayment;
import com.spendos.recurring.dto.RecurringDtos;
import com.spendos.recurring.repository.RecurringPaymentRepository;
import com.spendos.transactions.domain.Transaction;
import com.spendos.transactions.repository.TransactionRepository;
import com.spendos.users.service.UserPreferencesService;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Runs every detector for a user's month, ranks the findings and stores them. Importance =
 * (type weight + impact as a share of the month's spending, capped at 50) x confidence.
 */
@Component
public class InsightEngine {

    static final int BASELINE_MONTHS = 6;
    static final int EXPIRY_DAYS = 90;
    private static final Map<String, BigDecimal> TYPE_WEIGHT = Map.of(
            Insight.ANOMALY, BigDecimal.valueOf(30),
            Insight.MONEY_LEAK, BigDecimal.valueOf(25),
            Insight.OPPORTUNITY, BigDecimal.valueOf(20),
            Insight.SPENDING_TREND, BigDecimal.valueOf(15));

    private final TransactionRepository transactionRepository;
    private final RecurringPaymentRepository recurringRepository;
    private final InsightRepository insightRepository;
    private final AggregateQueries aggregates;
    private final UserPreferencesService preferencesService;
    private final PeriodResolver periods;
    private final ObjectMapper objectMapper;
    private final BigDecimal smallPurchaseLimit;

    public InsightEngine(TransactionRepository transactionRepository, RecurringPaymentRepository recurringRepository,
                         InsightRepository insightRepository, AggregateQueries aggregates,
                         UserPreferencesService preferencesService, PeriodResolver periods, ObjectMapper objectMapper,
                         @Value("${insights.small-purchase-limit:500}") BigDecimal smallPurchaseLimit) {
        this.transactionRepository = transactionRepository;
        this.recurringRepository = recurringRepository;
        this.insightRepository = insightRepository;
        this.aggregates = aggregates;
        this.preferencesService = preferencesService;
        this.periods = periods;
        this.objectMapper = objectMapper;
        this.smallPurchaseLimit = smallPurchaseLimit;
    }

    /** Recomputes and replaces the stored insights for the month. */
    @Transactional
    public List<Insight> generate(UUID userId, YearMonth month) {
        SpendingContext context = buildContext(userId, month);
        List<InsightDetector> detectors = List.of(
                new AnomalyDetector(AnomalyDetector.Sensitivity.MEDIUM),
                new MoneyLeakDetector(smallPurchaseLimit),
                new OpportunityDetector(),
                new SpendingTrendDetector());
        List<InsightCandidate> candidates = new ArrayList<>();
        detectors.forEach(detector -> candidates.addAll(detector.detect(context)));

        insightRepository.deleteForPeriod(userId, month.atDay(1), month.atEndOfMonth());
        BigDecimal monthExpense = context.currentExpense().max(BigDecimal.ONE);
        List<Insight> insights = candidates.stream()
                .map(candidate -> toEntity(userId, month, candidate, importance(candidate, monthExpense)))
                .sorted(Comparator.comparing(Insight::getImportanceScore).reversed())
                .toList();
        return insightRepository.saveAll(insights);
    }

    /** Builds the detector input: this month (to today) and up to six earlier months as the baseline. */
    @Transactional(readOnly = true)
    public SpendingContext buildContext(UUID userId, YearMonth month) {
        LocalDate start = month.atDay(1);
        LocalDate end = periods.effectiveEnd(userId, month);
        BigDecimal progress = end.equals(month.atEndOfMonth()) ? BigDecimal.ONE
                : BigDecimal.valueOf(end.getDayOfMonth()).divide(BigDecimal.valueOf(month.lengthOfMonth()), SpendingContext.MC);

        LocalDate firstActivity = aggregates.firstTransactionDate(userId);
        List<YearMonth> baselineMonths = new ArrayList<>();
        for (int i = BASELINE_MONTHS; i >= 1; i--) {
            YearMonth candidate = month.minusMonths(i);
            if (firstActivity != null && !candidate.isBefore(YearMonth.from(firstActivity))) {
                baselineMonths.add(candidate);
            }
        }
        List<Tx> current = spending(userId, start, end);
        List<Tx> history = baselineMonths.isEmpty() ? List.of()
                : spending(userId, baselineMonths.get(0).atDay(1), month.minusMonths(1).atEndOfMonth());

        List<RecurringPayment> recurring = recurringRepository.findByUserIdAndActiveTrue(userId);
        BigDecimal recurringMonthly = recurring.stream()
                .map(r -> RecurringDtos.monthlyCost(r.getTypicalAmount(), r.getFrequency()))
                .filter(java.util.Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        return new SpendingContext(userId, month, start, end, progress, current, history, baselineMonths,
                aggregates.totals(userId, start, end).income(), preferencesService.currencyFor(userId),
                recurringMonthly, recurring.size());
    }

    private List<Tx> spending(UUID userId, LocalDate start, LocalDate end) {
        return transactionRepository.findByUserIdAndTransactionDateBetweenOrderByTransactionDateAsc(userId, start, end)
                .stream()
                .filter(Transaction::isDebit)
                .filter(t -> t.getCategory() == null || !Category.TRANSFERS.equals(t.getCategory().getCategoryName()))
                .map(t -> new Tx(t.getId(), t.getTransactionDate(), t.getAmount(), t.getCategoryId(),
                        t.getCategory() != null ? t.getCategory().getCategoryName() : "Uncategorized",
                        t.getMerchantId(), t.getMerchant() != null ? t.getMerchant().getMerchantName() : t.getRawDescription()))
                .toList();
    }

    static BigDecimal importance(InsightCandidate candidate, BigDecimal monthExpense) {
        BigDecimal impact = candidate.impactValue() == null ? BigDecimal.ZERO : candidate.impactValue().abs();
        BigDecimal share = impact.multiply(BigDecimal.valueOf(100)).divide(monthExpense, 2, RoundingMode.HALF_UP)
                .min(BigDecimal.valueOf(50));
        return TYPE_WEIGHT.getOrDefault(candidate.type(), BigDecimal.TEN).add(share)
                .multiply(candidate.confidence()).setScale(2, RoundingMode.HALF_UP);
    }

    private Insight toEntity(UUID userId, YearMonth month, InsightCandidate candidate, BigDecimal importance) {
        Insight insight = new Insight();
        insight.setUserId(userId);
        insight.setInsightType(candidate.type());
        insight.setTitle(truncate(candidate.title(), 255));
        insight.setDescription(candidate.description());
        insight.setImpactValue(candidate.impactValue());
        insight.setImpactPercentage(clampPercent(candidate.impactPercentage()));
        insight.setRelatedCategoryId(candidate.categoryId());
        insight.setRelatedMerchantId(candidate.merchantId());
        insight.setActionable(candidate.actionable());
        insight.setSuggestedAction(candidate.suggestedAction());
        insight.setConfidence(candidate.confidence());
        insight.setPeriodStartDate(month.atDay(1));
        insight.setPeriodEndDate(month.atEndOfMonth());
        insight.setExpiresAt(LocalDateTime.of(month.atEndOfMonth().plusDays(EXPIRY_DAYS), java.time.LocalTime.MIDNIGHT));
        insight.setImportanceScore(importance);
        Map<String, Object> details = new LinkedHashMap<>(candidate.details() == null ? Map.of() : candidate.details());
        details.put("transactionIds", candidate.transactionIds());
        try {
            insight.setDetails(objectMapper.writeValueAsString(details));
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException(exception);
        }
        return insight;
    }

    /** impact_percentage is NUMERIC(5,2); keep extreme ratios inside the column range. */
    private static BigDecimal clampPercent(BigDecimal value) {
        if (value == null) {
            return null;
        }
        return value.max(BigDecimal.valueOf(-999)).min(BigDecimal.valueOf(999)).setScale(2, RoundingMode.HALF_UP);
    }

    private static String truncate(String value, int max) {
        return value.length() <= max ? value : value.substring(0, max);
    }
}

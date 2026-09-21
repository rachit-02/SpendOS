package com.spendos.health.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.spendos.analytics.repository.AggregateQueries;
import com.spendos.analytics.repository.AggregateQueries.CategoryAmount;
import com.spendos.analytics.service.PeriodResolver;
import com.spendos.common.exception.ApiException;
import com.spendos.common.util.Times;
import com.spendos.health.domain.FinancialHealthMetrics;
import com.spendos.health.dto.HealthMetricsDtos.Explanation;
import com.spendos.health.dto.HealthMetricsDtos.HealthMetrics;
import com.spendos.health.dto.HealthMetricsDtos.HistoryPoint;
import com.spendos.health.dto.HealthMetricsDtos.Metrics;
import com.spendos.health.dto.HealthMetricsDtos.Recommendation;
import com.spendos.health.repository.FinancialHealthMetricsRepository;
import com.spendos.health.service.FinancialHealthService.Snapshot;
import com.spendos.health.service.HealthAdvisor.ChangeSummary;
import com.spendos.health.service.HealthAdvisor.TopCategory;
import com.spendos.health.service.HealthScoreCalculator.Factor;
import com.spendos.health.service.HealthScoreCalculator.Result;
import com.spendos.users.service.UserPreferencesService;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Date;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Health score endpoints: the current score with its factors and advice, monthly history, and an
 * explanation of what changed. Every read also stores a snapshot (latest score in
 * financial_health_metrics, monthly scores in financial_health_history) so the score is tracked.
 */
@Service
public class HealthMetricsService {

    private static final int MAX_HISTORY_MONTHS = 24;
    private static final BigDecimal NUMERIC_5_2_MAX = new BigDecimal("999.99");
    private static final String DISABLED = "Health score is turned off in settings.";

    private final FinancialHealthService health;
    private final FinancialHealthMetricsRepository metricsRepository;
    private final AggregateQueries aggregates;
    private final PeriodResolver periods;
    private final UserPreferencesService preferences;
    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;

    public HealthMetricsService(FinancialHealthService health, FinancialHealthMetricsRepository metricsRepository,
                                AggregateQueries aggregates, PeriodResolver periods, UserPreferencesService preferences,
                                JdbcTemplate jdbc, ObjectMapper objectMapper) {
        this.health = health;
        this.metricsRepository = metricsRepository;
        this.aggregates = aggregates;
        this.periods = periods;
        this.preferences = preferences;
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public HealthMetrics current(UUID userId) {
        YearMonth month = YearMonth.from(periods.today(userId));
        if (!preferences.healthScoreEnabled(userId)) {
            return new HealthMetrics(false, month.toString(), null, DISABLED, List.of(), null, List.of(), null);
        }
        Snapshot snapshot = health.snapshot(userId, month);
        Result result = snapshot.result();
        LocalDateTime now = Times.nowUtc();
        if (result.score() != null) {
            saveLatest(userId, snapshot, now);
            saveHistory(userId, snapshot);
        }
        return new HealthMetrics(true, month.toString(), result.score(), result.summary(), result.factors(),
                metrics(snapshot), recommendations(userId, snapshot), Times.utc(now));
    }

    @Transactional
    public List<HistoryPoint> history(UUID userId, int months) {
        if (months < 1 || months > MAX_HISTORY_MONTHS) {
            throw ApiException.badRequest("INVALID_REQUEST", "months must be between 1 and " + MAX_HISTORY_MONTHS);
        }
        if (!preferences.healthScoreEnabled(userId)) {
            return List.of();
        }
        YearMonth latest = YearMonth.from(periods.today(userId));
        LocalDate first = aggregates.firstTransactionDate(userId);
        List<HistoryPoint> points = new ArrayList<>();
        for (YearMonth month = latest.minusMonths(months - 1L); !month.isAfter(latest); month = month.plusMonths(1)) {
            if (first == null || month.isBefore(YearMonth.from(first))) {
                continue;
            }
            Snapshot snapshot = health.snapshot(userId, month);
            if (snapshot.result().score() == null) {
                continue;
            }
            saveHistory(userId, snapshot);
            Map<String, BigDecimal> factors = new LinkedHashMap<>();
            snapshot.result().factors().stream().filter(Factor::scored).forEach(f -> factors.put(f.key(), f.points()));
            points.add(new HistoryPoint(month.toString(), month.getYear(), month.getMonthValue(), snapshot.result().score(),
                    factors));
        }
        return points;
    }

    @Transactional(readOnly = true)
    public Explanation explanation(UUID userId, Integer month, Integer year) {
        YearMonth target = periods.resolve(userId, month, year);
        YearMonth previousMonth = target.minusMonths(1);
        if (!preferences.healthScoreEnabled(userId)) {
            return new Explanation(false, target.toString(), previousMonth.toString(), null, null, null, DISABLED,
                    List.of(), List.of());
        }
        Snapshot current = health.snapshot(userId, target);
        Snapshot previous = health.snapshot(userId, previousMonth);
        ChangeSummary summary = HealthAdvisor.explain(current, previous);
        Integer now = current.result().score();
        Integer before = previous.result().score();
        return new Explanation(true, target.toString(), previousMonth.toString(), now, before,
                now == null || before == null ? null : now - before, summary.summary(), summary.changes(),
                recommendations(userId, current));
    }

    private List<Recommendation> recommendations(UUID userId, Snapshot snapshot) {
        if (snapshot.result().score() == null) {
            return List.of();
        }
        YearMonth start = snapshot.month().minusMonths(FinancialHealthService.WINDOW_MONTHS - 1L);
        List<CategoryAmount> categories = aggregates.spendingByCategory(userId, start.atDay(1),
                periods.effectiveEnd(userId, snapshot.month()));
        TopCategory top = categories.stream()
                .filter(c -> c.categoryId() != null && !"Other".equals(c.categoryName()))
                .findFirst()
                .map(c -> new TopCategory(c.categoryName(), c.amount().divide(
                        BigDecimal.valueOf(FinancialHealthService.WINDOW_MONTHS), 2, RoundingMode.HALF_UP)))
                .orElse(null);
        return HealthAdvisor.recommend(snapshot, top, preferences.currencyFor(userId));
    }

    private static Metrics metrics(Snapshot snapshot) {
        Result r = snapshot.result();
        return new Metrics(percent(r.savingsRate()), snapshot.inputs().averageMonthlyIncome(),
                snapshot.inputs().averageMonthlyExpense(), percent(r.volatility()), percent(r.recurringBurden()),
                r.bufferMonths() == null ? null : r.bufferMonths().setScale(1, RoundingMode.HALF_UP));
    }

    private void saveLatest(UUID userId, Snapshot snapshot, LocalDateTime now) {
        Result r = snapshot.result();
        FinancialHealthMetrics row = metricsRepository.findByUserId(userId).orElseGet(FinancialHealthMetrics::new);
        row.setUserId(userId);
        row.setHealthScore(r.score());
        row.setSavingsRate(fit(percent(r.savingsRate())));
        row.setAverageMonthlyIncome(snapshot.inputs().averageMonthlyIncome());
        row.setAverageMonthlyExpense(snapshot.inputs().averageMonthlyExpense());
        row.setSpendingVolatility(fit(percent(r.volatility())));
        row.setRecurringExpenseBurden(fit(percent(r.recurringBurden())));
        row.setEmergencyBufferMonths(fit(r.bufferMonths()));
        row.setScoreFactors(json(r.factors()));
        row.setCalculatedAt(now);
        row.setUpdatedAt(now);
        metricsRepository.save(row);
    }

    private void saveHistory(UUID userId, Snapshot snapshot) {
        jdbc.update("""
                INSERT INTO financial_health_history (user_id, period_month, health_score, score_factors, calculated_at)
                VALUES (?, ?, ?, CAST(? AS jsonb), CURRENT_TIMESTAMP)
                ON CONFLICT (user_id, period_month) DO UPDATE
                SET health_score = EXCLUDED.health_score, score_factors = EXCLUDED.score_factors,
                    calculated_at = EXCLUDED.calculated_at""",
                userId, Date.valueOf(snapshot.month().atDay(1)), snapshot.result().score(),
                json(snapshot.result().factors()));
    }

    private String json(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Could not serialise health factors", exception);
        }
    }

    private static BigDecimal percent(BigDecimal ratio) {
        return ratio == null ? null : ratio.multiply(BigDecimal.valueOf(100)).setScale(1, RoundingMode.HALF_UP);
    }

    /** Clamps to the NUMERIC(5,2) columns so an extreme ratio (e.g. spending 20x income) still saves. */
    private static BigDecimal fit(BigDecimal value) {
        if (value == null) {
            return null;
        }
        BigDecimal scaled = value.setScale(2, RoundingMode.HALF_UP);
        return scaled.max(NUMERIC_5_2_MAX.negate()).min(NUMERIC_5_2_MAX);
    }
}

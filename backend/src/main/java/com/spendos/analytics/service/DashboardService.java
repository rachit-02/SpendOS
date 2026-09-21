package com.spendos.analytics.service;

import com.spendos.analytics.dto.DashboardResponse;
import com.spendos.analytics.dto.DashboardResponse.CategorySpend;
import com.spendos.analytics.dto.DashboardResponse.Change;
import com.spendos.analytics.dto.DashboardResponse.FinancialHealth;
import com.spendos.analytics.dto.DashboardResponse.MerchantSpend;
import com.spendos.analytics.dto.DashboardResponse.Period;
import com.spendos.analytics.dto.DashboardResponse.RecentTransaction;
import com.spendos.analytics.dto.DashboardResponse.Spending;
import com.spendos.analytics.dto.DashboardResponse.Summary;
import com.spendos.analytics.dto.DashboardResponse.TrendPoint;
import com.spendos.analytics.repository.AggregateQueries;
import com.spendos.analytics.repository.AggregateQueries.CategoryAmount;
import com.spendos.analytics.repository.AggregateQueries.Totals;
import com.spendos.common.util.Money;
import com.spendos.common.util.Percentages;
import com.spendos.health.service.FinancialHealthService;
import com.spendos.health.service.HealthScoreCalculator;
import com.spendos.transactions.repository.TransactionRepository;
import com.spendos.transactions.repository.TransactionSpecifications;
import com.spendos.transactions.dto.TransactionFilter;
import com.spendos.users.service.UserPreferencesService;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DashboardService {

    static final int TREND_MONTHS = 6;
    static final BigDecimal TREND_THRESHOLD = BigDecimal.TEN;

    private final AggregateQueries aggregates;
    private final TransactionRepository transactionRepository;
    private final FinancialHealthService healthService;
    private final UserPreferencesService preferencesService;
    private final PeriodResolver periods;
    private final UserDataCache cache;
    private final ObjectProvider<DashboardSectionContributor> contributors;

    public DashboardService(AggregateQueries aggregates, TransactionRepository transactionRepository,
                            FinancialHealthService healthService, UserPreferencesService preferencesService,
                            PeriodResolver periods, UserDataCache cache,
                            ObjectProvider<DashboardSectionContributor> contributors) {
        this.aggregates = aggregates;
        this.transactionRepository = transactionRepository;
        this.healthService = healthService;
        this.preferencesService = preferencesService;
        this.periods = periods;
        this.cache = cache;
        this.contributors = contributors;
    }

    @Transactional(readOnly = true)
    public DashboardResponse dashboard(UUID userId, Integer month, Integer year) {
        YearMonth period = periods.resolve(userId, month, year);
        return cache.get(userId, "dashboard:" + period, () -> build(userId, period));
    }

    private DashboardResponse build(UUID userId, YearMonth month) {
        LocalDate start = month.atDay(1);
        LocalDate end = periods.effectiveEnd(userId, month);
        YearMonth previous = month.minusMonths(1);
        String currency = preferencesService.currencyFor(userId);

        Totals totals = aggregates.totals(userId, start, end);
        Totals previousTotals = aggregates.totals(userId, previous.atDay(1), previous.atEndOfMonth());
        BigDecimal net = totals.net();
        Summary summary = new Summary(Money.scale(totals.income()), Money.scale(totals.expense()), Money.scale(net),
                Money.ratio(net, totals.income()), currency, totals.incomeCount() + totals.expenseCount(),
                Money.percentChange(totals.expense(), previousTotals.expense()), Money.scale(previousTotals.expense()));

        Period periodInfo = new Period(start, end, month.getMonth().getDisplayName(TextStyle.FULL, Locale.ENGLISH),
                month.getMonthValue(), month.getYear(), month.equals(YearMonth.from(periods.today(userId))));

        Spending spending = new Spending(categorySpend(userId, month, start, end),
                aggregates.topMerchants(userId, start, end, null, 5).stream()
                        .map(m -> new MerchantSpend(m.merchantId(), m.merchantName(), Money.scale(m.amount()), m.count()))
                        .toList());

        List<TrendPoint> trend = aggregates.monthlyTotals(userId, month.minusMonths(TREND_MONTHS - 1L), month).stream()
                .map(t -> new TrendPoint(t.month().getMonth().getDisplayName(TextStyle.SHORT, Locale.ENGLISH),
                        t.month().getMonthValue(), t.month().getYear(), Money.scale(t.income()), Money.scale(t.expense())))
                .toList();

        List<RecentTransaction> recent = transactionRepository.findAll(
                        TransactionSpecifications.forUser(userId, new TransactionFilter(null, end, null, null, null, null,
                                null, null, null, null, null)),
                        PageRequest.of(0, 8, Sort.by(Sort.Direction.DESC, "transactionDate", "createdAt")))
                .map(t -> new RecentTransaction(t.getId(),
                        t.getMerchant() != null ? t.getMerchant().getMerchantName() : t.getRawDescription(),
                        t.getCategory() != null ? t.getCategory().getCategoryName() : null,
                        t.getCategory() != null ? t.getCategory().getColorHex() : null,
                        t.getAmount(), t.getTransactionDate(), t.getTransactionType()))
                .getContent();

        DashboardResponse response = new DashboardResponse(periodInfo, summary, health(userId, month), spending, trend,
                recent, new ArrayList<>(), new ArrayList<>(), new ArrayList<>());
        contributors.orderedStream().forEach(contributor -> contributor.contribute(userId, month, response));
        return response;
    }

    /** Category spend with percentages that sum to exactly 100 and a trend vs the previous month. */
    private List<CategorySpend> categorySpend(UUID userId, YearMonth month, LocalDate start, LocalDate end) {
        List<CategoryAmount> current = aggregates.spendingByCategory(userId, start, end);
        YearMonth previous = month.minusMonths(1);
        Map<UUID, BigDecimal> previousByCategory = aggregates.spendingByCategory(userId, previous.atDay(1),
                        previous.atEndOfMonth()).stream()
                .filter(c -> c.categoryId() != null)
                .collect(Collectors.toMap(CategoryAmount::categoryId, CategoryAmount::amount));
        List<BigDecimal> shares = Percentages.ofTotal(current.stream().map(CategoryAmount::amount).toList());
        List<CategorySpend> result = new ArrayList<>();
        for (int i = 0; i < current.size(); i++) {
            CategoryAmount c = current.get(i);
            BigDecimal before = previousByCategory.get(c.categoryId());
            result.add(new CategorySpend(c.categoryId(), c.categoryName(), c.colorHex(), Money.scale(c.amount()),
                    shares.get(i), c.count(), trend(c.amount(), before), before == null ? null : Money.scale(before)));
        }
        return result;
    }

    static String trend(BigDecimal current, BigDecimal previous) {
        if (previous == null || previous.signum() == 0) {
            return current.signum() > 0 ? "up" : "stable";
        }
        BigDecimal change = Money.percentChange(current, previous);
        if (change.compareTo(TREND_THRESHOLD) > 0) {
            return "up";
        }
        return change.compareTo(TREND_THRESHOLD.negate()) < 0 ? "down" : "stable";
    }

    /** Current score plus per-factor point changes versus the previous month's score. */
    private FinancialHealth health(UUID userId, YearMonth month) {
        if (!preferencesService.healthScoreEnabled(userId)) {
            return new FinancialHealth(null, Map.of(), List.of(), List.of(), "Health score is turned off in settings.");
        }
        HealthScoreCalculator.Result current = healthService.calculate(userId, month);
        HealthScoreCalculator.Result previous = healthService.calculate(userId, month.minusMonths(1));
        Map<String, BigDecimal> points = new LinkedHashMap<>();
        current.factors().stream().filter(HealthScoreCalculator.Factor::scored)
                .forEach(f -> points.put(f.key(), f.points()));
        List<Change> changes = new ArrayList<>();
        if (current.score() != null && previous.score() != null) {
            Map<String, HealthScoreCalculator.Factor> before = previous.factors().stream()
                    .collect(Collectors.toMap(HealthScoreCalculator.Factor::key, Function.identity()));
            for (HealthScoreCalculator.Factor factor : current.factors()) {
                HealthScoreCalculator.Factor old = before.get(factor.key());
                if (old == null || !factor.scored() || !old.scored()) {
                    continue;
                }
                BigDecimal delta = factor.points().subtract(old.points());
                if (delta.abs().compareTo(BigDecimal.ONE) >= 0) {
                    changes.add(new Change(factor.key(), delta.setScale(0, java.math.RoundingMode.HALF_UP),
                            reason(factor.label(), delta.signum() > 0)));
                }
            }
        }
        return new FinancialHealth(current.score(), points, current.factors(), changes, current.summary());
    }

    private static String reason(String label, boolean improved) {
        return label + (improved ? " improved" : " worsened") + " compared with last month";
    }
}

package com.spendos.analytics.service;

import com.spendos.analytics.dto.AnalyticsDtos.CategoryBreakdown;
import com.spendos.analytics.dto.AnalyticsDtos.CategoryChange;
import com.spendos.analytics.dto.AnalyticsDtos.CategoryStatistics;
import com.spendos.analytics.dto.AnalyticsDtos.CategoryTrend;
import com.spendos.analytics.dto.AnalyticsDtos.CategoryTrendPoint;
import com.spendos.analytics.dto.AnalyticsDtos.Comparison;
import com.spendos.analytics.dto.AnalyticsDtos.Expenses;
import com.spendos.analytics.dto.AnalyticsDtos.Income;
import com.spendos.analytics.dto.AnalyticsDtos.MerchantStat;
import com.spendos.analytics.dto.AnalyticsDtos.MethodBreakdown;
import com.spendos.analytics.dto.AnalyticsDtos.MonthComparison;
import com.spendos.analytics.dto.AnalyticsDtos.MonthSummary;
import com.spendos.analytics.dto.AnalyticsDtos.MonthlyAnalytics;
import com.spendos.analytics.dto.AnalyticsDtos.NamedAmount;
import com.spendos.analytics.dto.AnalyticsDtos.PeriodInfo;
import com.spendos.analytics.dto.AnalyticsDtos.SourceAmount;
import com.spendos.analytics.dto.AnalyticsDtos.SubcategoryShare;
import com.spendos.analytics.dto.AnalyticsDtos.TrendMonth;
import com.spendos.analytics.dto.AnalyticsDtos.Trends;
import com.spendos.analytics.repository.AggregateQueries;
import com.spendos.analytics.repository.AggregateQueries.CategoryAmount;
import com.spendos.analytics.repository.AggregateQueries.MethodAmount;
import com.spendos.analytics.repository.AggregateQueries.MonthAmount;
import com.spendos.analytics.repository.AggregateQueries.MonthTotals;
import com.spendos.analytics.repository.AggregateQueries.Totals;
import com.spendos.categories.domain.Category;
import com.spendos.categories.service.CategoryService;
import com.spendos.common.exception.ApiException;
import com.spendos.common.util.Money;
import com.spendos.common.util.Percentages;
import com.spendos.users.service.UserPreferencesService;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Deterministic analytics over the user's transactions. Every number is reproducible from the rows. */
@Service
public class AnalyticsService {

    public static final int MAX_TREND_MONTHS = 36;

    private final AggregateQueries aggregates;
    private final CategoryService categoryService;
    private final UserPreferencesService preferencesService;
    private final PeriodResolver periods;
    private final UserDataCache cache;

    public AnalyticsService(AggregateQueries aggregates, CategoryService categoryService,
                            UserPreferencesService preferencesService, PeriodResolver periods, UserDataCache cache) {
        this.aggregates = aggregates;
        this.categoryService = categoryService;
        this.preferencesService = preferencesService;
        this.periods = periods;
        this.cache = cache;
    }

    @Transactional(readOnly = true)
    public MonthlyAnalytics monthly(UUID userId, Integer month, Integer year) {
        YearMonth period = periods.resolve(userId, month, year);
        return cache.get(userId, "monthly:" + period, () -> buildMonthly(userId, period));
    }

    private MonthlyAnalytics buildMonthly(UUID userId, YearMonth month) {
        PeriodInfo period = period(userId, month);
        Totals totals = aggregates.totals(userId, period.startDate(), period.endDate());

        List<CategoryAmount> sources = aggregates.incomeBySource(userId, period.startDate(), period.endDate());
        List<BigDecimal> sourceShares = Percentages.ofTotal(sources.stream().map(CategoryAmount::amount).toList());
        List<SourceAmount> bySource = new ArrayList<>();
        for (int i = 0; i < sources.size(); i++) {
            bySource.add(new SourceAmount(sources.get(i).categoryName(), Money.scale(sources.get(i).amount()),
                    sourceShares.get(i)));
        }

        List<CategoryAmount> categories = aggregates.spendingByCategory(userId, period.startDate(), period.endDate());
        List<BigDecimal> categoryShares = Percentages.ofTotal(categories.stream().map(CategoryAmount::amount).toList());
        List<CategoryBreakdown> byCategory = new ArrayList<>();
        for (int i = 0; i < categories.size(); i++) {
            CategoryAmount c = categories.get(i);
            byCategory.add(new CategoryBreakdown(c.categoryId(), c.categoryName(), c.colorHex(), Money.scale(c.amount()),
                    categoryShares.get(i), c.count()));
        }

        List<MethodAmount> methods = aggregates.spendingByPaymentMethod(userId, period.startDate(), period.endDate());
        List<BigDecimal> methodShares = Percentages.ofTotal(methods.stream().map(MethodAmount::amount).toList());
        List<MethodBreakdown> byMethod = new ArrayList<>();
        for (int i = 0; i < methods.size(); i++) {
            byMethod.add(new MethodBreakdown(methods.get(i).method(), Money.scale(methods.get(i).amount()),
                    methodShares.get(i), methods.get(i).count()));
        }

        YearMonth previous = month.minusMonths(1);
        Totals previousTotals = aggregates.totals(userId, previous.atDay(1), previous.atEndOfMonth());
        List<CategoryChange> changes = categoryChanges(
                aggregates.spendingByCategory(userId, previous.atDay(1), previous.atEndOfMonth()), categories);
        BigDecimal expenseChange = Money.percentChange(totals.expense(), previousTotals.expense());
        Comparison comparison = new Comparison(Money.scale(previousTotals.income()), Money.scale(previousTotals.expense()),
                Money.percentChange(totals.income(), previousTotals.income()), expenseChange,
                DashboardService.trend(totals.expense(), previousTotals.expense()),
                changes);

        return new MonthlyAnalytics(period, new Income(Money.scale(totals.income()), bySource),
                new Expenses(Money.scale(totals.expense()), byCategory, byMethod), Money.scale(totals.net()),
                Money.ratio(totals.net(), totals.income()), comparison, preferencesService.currencyFor(userId));
    }

    @Transactional(readOnly = true)
    public CategoryTrend categoryTrend(UUID userId, UUID categoryId, int months) {
        if (months < 2 || months > MAX_TREND_MONTHS) {
            throw ApiException.badRequest("INVALID_REQUEST", "months must be between 2 and " + MAX_TREND_MONTHS);
        }
        Category category = categoryService.require(categoryId);
        YearMonth end = YearMonth.from(periods.today(userId));
        List<MonthAmount> series = aggregates.monthlyCategorySeries(userId, categoryId, end.minusMonths(months - 1L), end);
        List<CategoryTrendPoint> points = series.stream()
                .map(p -> new CategoryTrendPoint(monthName(p.month()), p.month().getMonthValue(), p.month().getYear(),
                        Money.scale(p.amount()), p.count()))
                .toList();

        List<BigDecimal> amounts = series.stream().map(MonthAmount::amount).toList();
        BigDecimal average = average(amounts);
        BigDecimal latest = amounts.get(amounts.size() - 1);
        BigDecimal earlierAverage = average(amounts.subList(0, amounts.size() - 1));
        return new CategoryTrend(categoryId, category.getCategoryName(), points, average,
                Money.percentChange(latest, earlierAverage), forecast(amounts),
                "Weighted average of the last three months (weights 3:2:1, most recent heaviest)");
    }

    /** Weighted average of the last three values (3:2:1); fewer values use what is available. */
    public static BigDecimal forecast(List<BigDecimal> amounts) {
        int[] weights = {3, 2, 1};
        BigDecimal weighted = BigDecimal.ZERO;
        int totalWeight = 0;
        for (int i = 0; i < Math.min(3, amounts.size()); i++) {
            weighted = weighted.add(amounts.get(amounts.size() - 1 - i).multiply(BigDecimal.valueOf(weights[i])));
            totalWeight += weights[i];
        }
        return totalWeight == 0 ? BigDecimal.ZERO : weighted.divide(BigDecimal.valueOf(totalWeight), 2, RoundingMode.HALF_UP);
    }

    @Transactional(readOnly = true)
    public List<MerchantStat> topMerchants(UUID userId, LocalDate start, LocalDate end, UUID categoryId, int limit) {
        LocalDate to = end != null ? end : periods.today(userId);
        LocalDate from = start != null ? start : to.minusDays(89);
        if (from.isAfter(to)) {
            throw ApiException.badRequest("INVALID_REQUEST", "startDate must not be after endDate");
        }
        if (limit < 1 || limit > 100) {
            throw ApiException.badRequest("INVALID_REQUEST", "limit must be between 1 and 100");
        }
        BigDecimal total = categoryId == null ? aggregates.totals(userId, from, to).expense()
                : aggregates.spendingByCategory(userId, from, to).stream()
                .filter(c -> categoryId.equals(c.categoryId())).map(CategoryAmount::amount)
                .findFirst().orElse(BigDecimal.ZERO);
        return aggregates.topMerchants(userId, from, to, categoryId, limit).stream()
                .map(m -> new MerchantStat(m.merchantId(), m.merchantName(), Money.scale(m.amount()), m.count(),
                        m.amount().divide(BigDecimal.valueOf(Math.max(1, m.count())), 2, RoundingMode.HALF_UP),
                        Money.percent(m.amount(), total)))
                .toList();
    }

    @Transactional(readOnly = true)
    public Trends trends(UUID userId, int months) {
        if (months < 2 || months > MAX_TREND_MONTHS) {
            throw ApiException.badRequest("INVALID_REQUEST", "months must be between 2 and " + MAX_TREND_MONTHS);
        }
        YearMonth end = YearMonth.from(periods.today(userId));
        YearMonth start = end.minusMonths(months - 1L);
        List<MonthTotals> series = aggregates.monthlyTotals(userId, start.minusMonths(12), end);
        Map<YearMonth, MonthTotals> byMonth = new HashMap<>();
        series.forEach(t -> byMonth.put(t.month(), t));

        List<TrendMonth> result = new ArrayList<>();
        for (YearMonth month = start; !month.isAfter(end); month = month.plusMonths(1)) {
            MonthTotals current = byMonth.get(month);
            MonthTotals lastYear = byMonth.get(month.minusYears(1));
            result.add(new TrendMonth(monthName(month), month.getMonthValue(), month.getYear(),
                    Money.scale(current.income()), Money.scale(current.expense()), Money.scale(current.net()),
                    Money.ratio(current.net(), current.income()), Money.scale(lastYear.income()),
                    Money.scale(lastYear.expense()), Money.percentChange(current.expense(), lastYear.expense())));
        }
        List<TrendMonth> active = result.stream()
                .filter(m -> m.income().signum() != 0 || m.expense().signum() != 0).toList();
        return new Trends(result,
                average(active.stream().map(TrendMonth::income).toList()),
                average(active.stream().map(TrendMonth::expense).toList()),
                active.stream().max(Comparator.comparing(TrendMonth::expense)).orElse(null),
                active.stream().min(Comparator.comparing(TrendMonth::expense)).orElse(null));
    }

    @Transactional(readOnly = true)
    public MonthComparison compare(UUID userId, YearMonth first, YearMonth second) {
        MonthSummary a = summary(userId, first);
        MonthSummary b = summary(userId, second);
        List<CategoryChange> categories = categoryChanges(
                aggregates.spendingByCategory(userId, a.period().startDate(), a.period().endDate()),
                aggregates.spendingByCategory(userId, b.period().startDate(), b.period().endDate()));
        return new MonthComparison(a, b, Money.scale(b.expense().subtract(a.expense())),
                Money.percentChange(b.expense(), a.expense()), categories);
    }

    @Transactional(readOnly = true)
    public CategoryStatistics categoryStatistics(UUID userId, UUID categoryId, LocalDate start, LocalDate end) {
        Category category = categoryService.require(categoryId);
        LocalDate to = end != null ? end : periods.today(userId);
        LocalDate from = start != null ? start : to.withDayOfMonth(1);
        if (from.isAfter(to)) {
            throw ApiException.badRequest("INVALID_REQUEST", "startDate must not be after endDate");
        }
        CategoryAmount stats = aggregates.spendingByCategory(userId, from, to).stream()
                .filter(c -> categoryId.equals(c.categoryId())).findFirst()
                .orElse(new CategoryAmount(categoryId, category.getCategoryName(), category.getColorHex(), BigDecimal.ZERO, 0));
        BigDecimal totalSpend = aggregates.totals(userId, from, to).expense();
        List<AggregateQueries.SubcategoryAmount> subs = aggregates.spendingBySubcategory(userId, categoryId, from, to);
        List<BigDecimal> shares = Percentages.ofTotal(subs.stream().map(AggregateQueries.SubcategoryAmount::amount).toList());
        List<SubcategoryShare> breakdown = new ArrayList<>();
        for (int i = 0; i < subs.size(); i++) {
            breakdown.add(new SubcategoryShare(subs.get(i).subcategoryName(), Money.scale(subs.get(i).amount()), shares.get(i)));
        }
        return new CategoryStatistics(categoryId, category.getCategoryName(), from, to, Money.scale(stats.amount()),
                stats.count(),
                stats.count() == 0 ? BigDecimal.ZERO.setScale(2)
                        : stats.amount().divide(BigDecimal.valueOf(stats.count()), 2, RoundingMode.HALF_UP),
                Money.percent(stats.amount(), totalSpend),
                aggregates.topMerchants(userId, from, to, categoryId, 5).stream()
                        .map(m -> new NamedAmount(m.merchantName(), Money.scale(m.amount()), m.count())).toList(),
                breakdown);
    }

    private MonthSummary summary(UUID userId, YearMonth month) {
        PeriodInfo period = period(userId, month);
        Totals totals = aggregates.totals(userId, period.startDate(), period.endDate());
        return new MonthSummary(period, Money.scale(totals.income()), Money.scale(totals.expense()),
                Money.scale(totals.net()), Money.ratio(totals.net(), totals.income()));
    }

    /** Per-category change between two periods, largest absolute change first. */
    private static List<CategoryChange> categoryChanges(List<CategoryAmount> before, List<CategoryAmount> after) {
        Map<String, CategoryAmount[]> byKey = new LinkedHashMap<>();
        for (CategoryAmount c : after) {
            byKey.computeIfAbsent(key(c), k -> new CategoryAmount[2])[1] = c;
        }
        for (CategoryAmount c : before) {
            byKey.computeIfAbsent(key(c), k -> new CategoryAmount[2])[0] = c;
        }
        return byKey.values().stream().map(pair -> {
                    CategoryAmount any = pair[1] != null ? pair[1] : pair[0];
                    BigDecimal previous = pair[0] == null ? BigDecimal.ZERO : pair[0].amount();
                    BigDecimal current = pair[1] == null ? BigDecimal.ZERO : pair[1].amount();
                    return new CategoryChange(any.categoryId(), any.categoryName(), Money.scale(previous),
                            Money.scale(current), Money.scale(current.subtract(previous)),
                            Money.percentChange(current, previous));
                })
                .sorted(Comparator.comparing((CategoryChange c) -> c.change().abs()).reversed())
                .toList();
    }

    private static String key(CategoryAmount c) {
        return Objects.toString(c.categoryId(), c.categoryName());
    }

    private PeriodInfo period(UUID userId, YearMonth month) {
        return new PeriodInfo(month.getMonth().getDisplayName(TextStyle.FULL, Locale.ENGLISH), month.getMonthValue(),
                month.getYear(), month.atDay(1), periods.effectiveEnd(userId, month));
    }

    private static String monthName(YearMonth month) {
        return month.getMonth().getDisplayName(TextStyle.SHORT, Locale.ENGLISH);
    }

    private static BigDecimal average(List<BigDecimal> values) {
        if (values.isEmpty()) {
            return BigDecimal.ZERO.setScale(2);
        }
        return values.stream().reduce(BigDecimal.ZERO, BigDecimal::add)
                .divide(BigDecimal.valueOf(values.size()), 2, RoundingMode.HALF_UP);
    }
}

package com.spendos.reports.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.spendos.analytics.repository.AggregateQueries;
import com.spendos.analytics.repository.AggregateQueries.CategoryAmount;
import com.spendos.analytics.repository.AggregateQueries.Totals;
import com.spendos.analytics.service.PeriodResolver;
import com.spendos.budgets.dto.BudgetDtos.BudgetResponse;
import com.spendos.budgets.dto.BudgetDtos.CategoryProgress;
import com.spendos.budgets.service.BudgetService;
import com.spendos.common.exception.ApiException;
import com.spendos.common.util.Money;
import com.spendos.common.util.Percentages;
import com.spendos.common.util.Times;
import com.spendos.health.service.FinancialHealthService;
import com.spendos.insights.detectors.AnomalyDetector;
import com.spendos.insights.detectors.InsightCandidate;
import com.spendos.insights.domain.Insight;
import com.spendos.insights.engine.InsightEngine;
import com.spendos.insights.service.InsightService;
import com.spendos.recurring.dto.RecurringDtos.RecurringResponse;
import com.spendos.recurring.service.RecurringService;
import com.spendos.reports.domain.MonthlyReport;
import com.spendos.reports.dto.AutopsyDtos.BudgetLine;
import com.spendos.reports.dto.AutopsyDtos.CategoryDelta;
import com.spendos.reports.dto.AutopsyDtos.CategoryLine;
import com.spendos.reports.dto.AutopsyDtos.Changes;
import com.spendos.reports.dto.AutopsyDtos.MerchantLine;
import com.spendos.reports.dto.AutopsyDtos.MonthlyAutopsy;
import com.spendos.reports.dto.AutopsyDtos.RecurringLine;
import com.spendos.reports.dto.AutopsyDtos.ReportSummary;
import com.spendos.reports.dto.AutopsyDtos.UnusualLine;
import com.spendos.reports.dto.AutopsyDtos.WatchItem;
import com.spendos.reports.repository.MonthlyReportRepository;
import com.spendos.transactions.service.TransactionsChangedEvent;
import com.spendos.users.service.UserPreferencesService;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Builds the end-of-month "money autopsy". Every figure is derived from the user's transactions,
 * budgets and recurring payments; the only prose comes from deterministic insight templates.
 * Reports are stored and reused until the underlying data changes.
 */
@Service
public class AutopsyService {

    static final long PROVISIONAL_TTL_SECONDS = 15 * 60;

    private final MonthlyReportRepository reportRepository;
    private final AggregateQueries aggregates;
    private final BudgetService budgetService;
    private final RecurringService recurringService;
    private final InsightService insightService;
    private final InsightEngine insightEngine;
    private final FinancialHealthService healthService;
    private final UserPreferencesService preferencesService;
    private final PeriodResolver periods;
    private final ObjectMapper objectMapper;
    private final Map<UUID, Instant> lastDataChange = new ConcurrentHashMap<>();

    public AutopsyService(MonthlyReportRepository reportRepository, AggregateQueries aggregates,
                          BudgetService budgetService, RecurringService recurringService, InsightService insightService,
                          InsightEngine insightEngine, FinancialHealthService healthService,
                          UserPreferencesService preferencesService, PeriodResolver periods, ObjectMapper objectMapper) {
        this.reportRepository = reportRepository;
        this.aggregates = aggregates;
        this.budgetService = budgetService;
        this.recurringService = recurringService;
        this.insightService = insightService;
        this.insightEngine = insightEngine;
        this.healthService = healthService;
        this.preferencesService = preferencesService;
        this.periods = periods;
        this.objectMapper = objectMapper;
    }

    @EventListener
    public void onTransactionsChanged(TransactionsChangedEvent event) {
        lastDataChange.put(event.userId(), Instant.now());
    }

    /** Returns the stored report when still valid, otherwise regenerates it. */
    @Transactional
    public MonthlyAutopsy get(UUID userId, Integer month, Integer year) {
        YearMonth period = periods.resolve(userId, month, year);
        MonthlyReport stored = reportRepository
                .findByUserIdAndPeriodYearAndPeriodMonth(userId, period.getYear(), period.getMonthValue()).orElse(null);
        if (stored != null && isFresh(userId, stored)) {
            return read(stored);
        }
        return generate(userId, period);
    }

    @Transactional
    public MonthlyAutopsy generate(UUID userId, YearMonth month) {
        YearMonth current = YearMonth.from(periods.today(userId));
        if (month.isAfter(current)) {
            throw ApiException.badRequest("INVALID_REQUEST", "Cannot build a report for a future month");
        }
        MonthlyAutopsy autopsy = build(userId, month, !month.equals(current));
        MonthlyReport report = reportRepository
                .findByUserIdAndPeriodYearAndPeriodMonth(userId, month.getYear(), month.getMonthValue())
                .orElseGet(MonthlyReport::new);
        report.setUserId(userId);
        report.setPeriodYear(month.getYear());
        report.setPeriodMonth(month.getMonthValue());
        report.setComplete(autopsy.isComplete());
        report.setGeneratedAt(Times.nowUtc());
        try {
            report.setReportJson(objectMapper.writeValueAsString(autopsy));
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException(exception);
        }
        reportRepository.save(report);
        return autopsy;
    }

    @Transactional(readOnly = true)
    public List<ReportSummary> list(UUID userId) {
        return reportRepository.findByUserIdOrderByPeriodYearDescPeriodMonthDesc(userId).stream()
                .map(r -> new ReportSummary(r.getPeriodYear(), r.getPeriodMonth(),
                        YearMonth.of(r.getPeriodYear(), r.getPeriodMonth()).getMonth()
                                .getDisplayName(TextStyle.FULL, Locale.ENGLISH) + " " + r.getPeriodYear(),
                        r.isComplete(), Times.utc(r.getGeneratedAt())))
                .toList();
    }

    MonthlyAutopsy build(UUID userId, YearMonth month, boolean complete) {
        LocalDate start = month.atDay(1);
        LocalDate end = periods.effectiveEnd(userId, month);
        YearMonth previousMonth = month.minusMonths(1);
        String currency = preferencesService.currencyFor(userId);

        Totals totals = aggregates.totals(userId, start, end);
        Totals previous = aggregates.totals(userId, previousMonth.atDay(1), previousMonth.atEndOfMonth());
        List<CategoryAmount> categories = aggregates.spendingByCategory(userId, start, end);
        List<CategoryAmount> previousCategories = aggregates.spendingByCategory(userId, previousMonth.atDay(1),
                previousMonth.atEndOfMonth());

        List<BigDecimal> shares = Percentages.ofTotal(categories.stream().map(CategoryAmount::amount).toList());
        List<CategoryLine> byCategory = new ArrayList<>();
        for (int i = 0; i < categories.size(); i++) {
            CategoryAmount c = categories.get(i);
            byCategory.add(new CategoryLine(c.categoryName(), c.colorHex(), Money.scale(c.amount()), shares.get(i)));
        }

        Changes changes = changes(previousCategories, categories);
        List<MerchantLine> merchants = aggregates.topMerchants(userId, start, end, null, 5).stream()
                .map(m -> new MerchantLine(m.merchantName(), Money.scale(m.amount()), m.count())).toList();

        List<RecurringResponse> recurring = recurringService.list(userId, "all", "amount").stream()
                .filter(RecurringResponse::isActive).toList();
        List<RecurringLine> recurringLines = recurring.stream()
                .map(r -> new RecurringLine(r.merchantName(), r.typicalAmount(), r.frequency(), r.monthlyCost())).toList();

        List<UnusualLine> unusual = new AnomalyDetector(AnomalyDetector.Sensitivity.MEDIUM)
                .detect(insightEngine.buildContext(userId, month)).stream()
                .filter(c -> "purchase".equals(c.details().get("kind")))
                .map(AutopsyService::unusualLine)
                .toList();

        Map<String, BudgetLine> budgets = budgetPerformance(userId, month);

        List<Insight> insights = insightService.fresh(userId, month);
        Insight top = insights.isEmpty() ? null : insights.get(0);

        BigDecimal net = totals.net();
        List<WatchItem> watchlist = watchlist(currency, changes, recurring, budgets, net);
        Integer healthScore = preferencesService.healthScoreEnabled(userId)
                ? healthService.calculate(userId, month).score() : null;

        return new MonthlyAutopsy(
                month.getMonth().getDisplayName(TextStyle.FULL, Locale.ENGLISH) + " " + month.getYear(),
                month.getYear(), month.getMonthValue(), start, end, complete, currency,
                Money.scale(totals.income()), Money.scale(totals.expense()), Money.scale(net),
                Money.ratio(net, totals.income()), Money.scale(previous.expense()),
                Money.percentChange(totals.expense(), previous.expense()), healthScore,
                byCategory, changes, merchants, recurringLines, unusual, budgets,
                top == null ? defaultInsight(totals) : top.getDescription(),
                top == null || top.getSuggestedAction() == null ? defaultAction(totals) : top.getSuggestedAction(),
                watchlist, totals.incomeCount() + totals.expenseCount(), Instant.now());
    }

    static Changes changes(List<CategoryAmount> before, List<CategoryAmount> after) {
        Map<String, BigDecimal[]> byName = new LinkedHashMap<>();
        after.forEach(c -> byName.computeIfAbsent(c.categoryName(), k -> new BigDecimal[] {BigDecimal.ZERO, BigDecimal.ZERO})[1] = c.amount());
        before.forEach(c -> byName.computeIfAbsent(c.categoryName(), k -> new BigDecimal[] {BigDecimal.ZERO, BigDecimal.ZERO})[0] = c.amount());
        List<CategoryDelta> deltas = byName.entrySet().stream()
                .map(e -> new CategoryDelta(e.getKey(), Money.scale(e.getValue()[0]), Money.scale(e.getValue()[1]),
                        Money.scale(e.getValue()[1].subtract(e.getValue()[0])),
                        Money.percentChange(e.getValue()[1], e.getValue()[0])))
                .toList();
        List<CategoryDelta> increases = deltas.stream().filter(d -> d.amount().signum() > 0)
                .sorted(Comparator.comparing(CategoryDelta::amount).reversed()).limit(3).toList();
        List<CategoryDelta> decreases = deltas.stream().filter(d -> d.amount().signum() < 0)
                .sorted(Comparator.comparing(CategoryDelta::amount)).limit(3).toList();
        return new Changes(increases, decreases);
    }

    private Map<String, BudgetLine> budgetPerformance(UUID userId, YearMonth month) {
        Map<String, BudgetLine> lines = new LinkedHashMap<>();
        for (BudgetResponse budget : budgetService.list(userId, null)) {
            if (budget.startDate().isAfter(month.atEndOfMonth()) || budget.endDate().isBefore(month.atDay(1))) {
                continue;
            }
            if (budget.categories().isEmpty()) {
                lines.put(budget.budgetName(), new BudgetLine(budget.totalAmount(), budget.spentAmount(),
                        budget.percentage(), budget.isExceeded()));
            } else {
                for (CategoryProgress c : budget.categories()) {
                    lines.put(c.categoryName(), new BudgetLine(c.allocatedAmount(), c.spentAmount(), c.percentage(),
                            c.isExceeded()));
                }
            }
        }
        return lines;
    }

    private static List<WatchItem> watchlist(String currency, Changes changes, List<RecurringResponse> recurring,
                                             Map<String, BudgetLine> budgets, BigDecimal net) {
        List<WatchItem> items = new ArrayList<>();
        BigDecimal recurringTotal = recurring.stream().map(RecurringResponse::monthlyCost).filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        if (recurringTotal.signum() > 0) {
            items.add(new WatchItem("recurring", "About " + money(recurringTotal, currency) + " of recurring payments ("
                    + recurring.size() + ") will be due next month.", Money.scale(recurringTotal)));
        }
        changes.largestIncreases().stream()
                .filter(d -> d.percentageChange() != null && d.percentageChange().compareTo(BigDecimal.valueOf(20)) >= 0)
                .findFirst()
                .ifPresent(d -> items.add(new WatchItem("category", d.category() + " spending rose "
                        + d.percentageChange().setScale(0, RoundingMode.HALF_UP) + "% this month; keep an eye on it.",
                        d.amount())));
        budgets.forEach((name, line) -> {
            if (line.exceeded()) {
                items.add(new WatchItem("budget", "You went over your " + name + " budget by "
                        + money(line.spent().subtract(line.budget()), currency) + ". Adjust the limit or your plans.",
                        Money.scale(line.spent().subtract(line.budget()))));
            }
        });
        if (net.signum() < 0) {
            items.add(new WatchItem("savings", "You spent " + money(net.negate(), currency)
                    + " more than you earned. Plan next month with a budget.", Money.scale(net.negate())));
        }
        return items;
    }

    private static UnusualLine unusualLine(InsightCandidate candidate) {
        Map<String, Object> details = candidate.details();
        return new UnusualLine(candidate.description(), candidate.impactValue(),
                LocalDate.parse(String.valueOf(details.get("date"))),
                details.get("merchantName") == null ? null : String.valueOf(details.get("merchantName")),
                String.valueOf(details.get("categoryName")));
    }

    private static String defaultInsight(Totals totals) {
        if (totals.expenseCount() == 0) {
            return "No spending was recorded this month.";
        }
        return "Nothing unusual stood out this month. Your spending followed your usual patterns.";
    }

    private static String defaultAction(Totals totals) {
        return totals.net().signum() >= 0
                ? "Consider moving part of this month's savings into a savings goal."
                : "Set a budget for your largest category to bring spending below income.";
    }

    private boolean isFresh(UUID userId, MonthlyReport stored) {
        Instant generated = Times.utc(stored.getGeneratedAt());
        Instant changed = lastDataChange.get(userId);
        if (changed != null && changed.isAfter(generated)) {
            return false;
        }
        return stored.isComplete() || generated.isAfter(Instant.now().minusSeconds(PROVISIONAL_TTL_SECONDS));
    }

    private MonthlyAutopsy read(MonthlyReport stored) {
        try {
            return objectMapper.readValue(stored.getReportJson(), MonthlyAutopsy.class);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Stored report is unreadable", exception);
        }
    }

    private static String money(BigDecimal amount, String currency) {
        java.text.NumberFormat format = java.text.NumberFormat.getCurrencyInstance(
                "INR".equals(currency) ? new Locale("en", "IN") : Locale.US);
        try {
            format.setCurrency(java.util.Currency.getInstance(currency));
        } catch (IllegalArgumentException ignored) {
            // locale default
        }
        format.setMaximumFractionDigits(0);
        return format.format(amount);
    }
}

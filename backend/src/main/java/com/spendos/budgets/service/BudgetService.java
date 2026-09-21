package com.spendos.budgets.service;

import com.spendos.analytics.repository.AggregateQueries;
import com.spendos.analytics.repository.AggregateQueries.CategoryAmount;
import com.spendos.analytics.service.PeriodResolver;
import com.spendos.analytics.service.UserDataCache;
import com.spendos.audit.service.AuditService;
import com.spendos.budgets.domain.Budget;
import com.spendos.budgets.domain.BudgetCategory;
import com.spendos.budgets.dto.BudgetDtos.BudgetAlert;
import com.spendos.budgets.dto.BudgetDtos.BudgetProgress;
import com.spendos.budgets.dto.BudgetDtos.BudgetRequest;
import com.spendos.budgets.dto.BudgetDtos.BudgetResponse;
import com.spendos.budgets.dto.BudgetDtos.CategoryAllocation;
import com.spendos.budgets.dto.BudgetDtos.CategoryProgress;
import com.spendos.budgets.repository.BudgetCategoryRepository;
import com.spendos.budgets.repository.BudgetRepository;
import com.spendos.categories.domain.Category;
import com.spendos.categories.service.CategoryService;
import com.spendos.common.exception.ApiException;
import com.spendos.common.util.Money;
import com.spendos.common.util.Times;
import com.spendos.health.service.BudgetAdherenceProvider;
import com.spendos.users.service.UserPreferencesService;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Budgets with an overall limit and optional per-category allocations. A budget without categories
 * limits all spending in its period; with categories, it limits spending in those categories.
 */
@Service
public class BudgetService implements BudgetAdherenceProvider {

    private final BudgetRepository budgetRepository;
    private final BudgetCategoryRepository budgetCategoryRepository;
    private final CategoryService categoryService;
    private final AggregateQueries aggregates;
    private final UserPreferencesService preferencesService;
    private final PeriodResolver periods;
    private final AuditService auditService;
    private final UserDataCache cache;

    public BudgetService(BudgetRepository budgetRepository, BudgetCategoryRepository budgetCategoryRepository,
                         CategoryService categoryService, AggregateQueries aggregates,
                         UserPreferencesService preferencesService, PeriodResolver periods, AuditService auditService,
                         UserDataCache cache) {
        this.budgetRepository = budgetRepository;
        this.budgetCategoryRepository = budgetCategoryRepository;
        this.categoryService = categoryService;
        this.aggregates = aggregates;
        this.preferencesService = preferencesService;
        this.periods = periods;
        this.auditService = auditService;
        this.cache = cache;
    }

    @Transactional(readOnly = true)
    public List<BudgetResponse> list(UUID userId, Boolean active) {
        List<Budget> budgets = budgetRepository.findByUserId(userId).stream()
                .filter(b -> active == null || b.isActive() == active)
                .sorted(Comparator.comparing(Budget::getStartDate).reversed().thenComparing(Budget::getBudgetName))
                .toList();
        Map<UUID, List<BudgetCategory>> allocations = allocationsFor(budgets);
        return budgets.stream().map(b -> toResponse(b, allocations.getOrDefault(b.getId(), List.of()))).toList();
    }

    @Transactional(readOnly = true)
    public BudgetResponse get(UUID userId, UUID budgetId) {
        Budget budget = require(userId, budgetId);
        return toResponse(budget, budgetCategoryRepository.findByBudgetId(budgetId));
    }

    @Transactional
    public BudgetResponse create(UUID userId, BudgetRequest request) {
        Budget budget = new Budget();
        budget.setUserId(userId);
        budget.setCurrencyCode(preferencesService.currencyFor(userId));
        apply(budget, request);
        Budget saved = budgetRepository.save(budget);
        List<BudgetCategory> allocations = saveAllocations(saved, request.categories());
        auditService.record(userId, "budget", saved.getId(), AuditService.CREATE, null, snapshot(saved));
        cache.evict(userId);
        return toResponse(saved, allocations);
    }

    @Transactional
    public BudgetResponse update(UUID userId, UUID budgetId, BudgetRequest request) {
        Budget budget = require(userId, budgetId);
        Map<String, Object> before = snapshot(budget);
        apply(budget, request);
        Budget saved = budgetRepository.save(budget);
        List<BudgetCategory> allocations = request.categories() == null
                ? budgetCategoryRepository.findByBudgetId(budgetId)
                : replaceAllocations(saved, request.categories());
        auditService.record(userId, "budget", budgetId, AuditService.UPDATE, before, snapshot(saved));
        cache.evict(userId);
        return toResponse(saved, allocations);
    }

    @Transactional
    public void delete(UUID userId, UUID budgetId) {
        Budget budget = require(userId, budgetId);
        budgetCategoryRepository.deleteByBudgetId(budgetId);
        budgetRepository.delete(budget);
        auditService.record(userId, "budget", budgetId, AuditService.DELETE, snapshot(budget), null);
        cache.evict(userId);
    }

    @Transactional(readOnly = true)
    public BudgetProgress progress(UUID userId, UUID budgetId) {
        BudgetResponse budget = get(userId, budgetId);
        LocalDate today = periods.today(userId);
        int daysTotal = (int) ChronoUnit.DAYS.between(budget.startDate(), budget.endDate()) + 1;
        LocalDate through = today.isBefore(budget.startDate()) ? budget.startDate().minusDays(1)
                : today.isAfter(budget.endDate()) ? budget.endDate() : today;
        int daysElapsed = (int) Math.max(0, ChronoUnit.DAYS.between(budget.startDate(), through) + 1);
        int daysRemaining = daysTotal - daysElapsed;

        BigDecimal expectedToDate = budget.totalAmount().multiply(BigDecimal.valueOf(daysElapsed))
                .divide(BigDecimal.valueOf(daysTotal), 2, RoundingMode.HALF_UP);
        BigDecimal projected = daysElapsed == 0 ? BigDecimal.ZERO.setScale(2)
                : budget.spentAmount().multiply(BigDecimal.valueOf(daysTotal))
                .divide(BigDecimal.valueOf(daysElapsed), 2, RoundingMode.HALF_UP);
        BigDecimal allowance = daysRemaining <= 0 ? BigDecimal.ZERO.setScale(2)
                : budget.remainingAmount().max(BigDecimal.ZERO)
                .divide(BigDecimal.valueOf(daysRemaining), 2, RoundingMode.HALF_UP);
        String pace = daysElapsed == 0 ? "not_started"
                : budget.spentAmount().compareTo(expectedToDate.multiply(new BigDecimal("1.10"))) > 0 ? "ahead"
                : budget.spentAmount().compareTo(expectedToDate.multiply(new BigDecimal("0.90"))) < 0 ? "behind"
                : "on_track";
        return new BudgetProgress(budget, daysElapsed, daysTotal, Math.max(0, daysRemaining), expectedToDate, projected,
                projected.compareTo(budget.totalAmount()) > 0, allowance, pace);
    }

    /** Budgets or budget categories at/over their alert threshold in currently running budgets. */
    @Transactional(readOnly = true)
    public List<BudgetAlert> alerts(UUID userId) {
        LocalDate today = periods.today(userId);
        List<BudgetAlert> alerts = new ArrayList<>();
        for (BudgetResponse budget : list(userId, true)) {
            if (today.isBefore(budget.startDate()) || today.isAfter(budget.endDate())) {
                continue;
            }
            if (budget.isAlert() || budget.isExceeded()) {
                alerts.add(alert(budget, null, null, budget.totalAmount(), budget.spentAmount(), budget.percentage(),
                        budget.isExceeded()));
            }
            for (CategoryProgress category : budget.categories()) {
                if (category.isAlert() || category.isExceeded()) {
                    alerts.add(alert(budget, category.categoryId(), category.categoryName(), category.allocatedAmount(),
                            category.spentAmount(), category.percentage(), category.isExceeded()));
                }
            }
        }
        alerts.sort(Comparator.comparing(BudgetAlert::percentage).reversed());
        return alerts;
    }

    /** Health-score input: share of budget limits (per category, or overall) not exceeded. */
    @Override
    @Transactional(readOnly = true)
    public Adherence adherence(UUID userId, LocalDate start, LocalDate end) {
        List<BudgetResponse> overlapping = list(userId, null).stream()
                .filter(b -> !b.startDate().isAfter(end) && !b.endDate().isBefore(start))
                .toList();
        int tracked = 0;
        int respected = 0;
        for (BudgetResponse budget : overlapping) {
            if (budget.categories().isEmpty()) {
                tracked++;
                respected += budget.isExceeded() ? 0 : 1;
            } else {
                for (CategoryProgress category : budget.categories()) {
                    tracked++;
                    respected += category.isExceeded() ? 0 : 1;
                }
            }
        }
        return tracked == 0 ? Adherence.none()
                : new Adherence(BigDecimal.valueOf(respected).divide(BigDecimal.valueOf(tracked), 4, RoundingMode.HALF_UP),
                tracked);
    }

    private BudgetResponse toResponse(Budget budget, List<BudgetCategory> allocations) {
        UUID userId = budget.getUserId();
        Map<UUID, CategoryAmount> spentByCategory = aggregates
                .spendingByCategory(userId, budget.getStartDate(), budget.getEndDate()).stream()
                .filter(c -> c.categoryId() != null)
                .collect(Collectors.toMap(CategoryAmount::categoryId, Function.identity()));
        int threshold = budget.getAlertThresholdPercent() == null ? 90 : budget.getAlertThresholdPercent();

        List<CategoryProgress> categories = new ArrayList<>();
        BigDecimal spent;
        if (allocations.isEmpty()) {
            spent = aggregates.totals(userId, budget.getStartDate(), budget.getEndDate()).expense();
        } else {
            spent = BigDecimal.ZERO;
            for (BudgetCategory allocation : allocations) {
                Category category = categoryService.require(allocation.getCategoryId());
                CategoryAmount amount = spentByCategory.get(allocation.getCategoryId());
                BigDecimal categorySpent = amount == null ? BigDecimal.ZERO : amount.amount();
                spent = spent.add(categorySpent);
                BigDecimal percentage = Money.percent(categorySpent, allocation.getAllocatedAmount());
                categories.add(new CategoryProgress(category.getId(), category.getCategoryName(), category.getColorHex(),
                        Money.scale(allocation.getAllocatedAmount()), Money.scale(categorySpent),
                        Money.scale(allocation.getAllocatedAmount().subtract(categorySpent)), percentage,
                        categorySpent.compareTo(allocation.getAllocatedAmount()) > 0,
                        percentage.compareTo(BigDecimal.valueOf(threshold)) >= 0));
            }
            categories.sort(Comparator.comparing(CategoryProgress::percentage).reversed());
        }
        BigDecimal percentage = Money.percent(spent, budget.getTotalAmount());
        boolean exceeded = spent.compareTo(budget.getTotalAmount()) > 0;
        boolean alert = percentage.compareTo(BigDecimal.valueOf(threshold)) >= 0;
        String status = exceeded ? "exceeded" : alert ? "warning" : "on_track";
        return new BudgetResponse(budget.getId(), budget.getBudgetName(), budget.getBudgetType(),
                Money.scale(budget.getTotalAmount()), budget.getCurrencyCode(), budget.getStartDate(), budget.getEndDate(),
                threshold, budget.isActive(), Money.scale(spent), Money.scale(budget.getTotalAmount().subtract(spent)),
                percentage, exceeded, alert, status, categories, Times.utc(budget.getCreatedAt()),
                Times.utc(budget.getUpdatedAt()));
    }

    private void apply(Budget budget, BudgetRequest request) {
        String type = request.budgetType() == null ? "monthly" : request.budgetType();
        LocalDate end = request.endDate() != null ? request.endDate() : switch (type) {
            case "monthly" -> request.startDate().plusMonths(1).minusDays(1);
            case "quarterly" -> request.startDate().plusMonths(3).minusDays(1);
            case "annual" -> request.startDate().plusYears(1).minusDays(1);
            default -> throw ApiException.badRequest("INVALID_REQUEST", "endDate is required for custom budgets");
        };
        if (!request.startDate().isBefore(end)) {
            throw ApiException.badRequest("INVALID_REQUEST", "startDate must be before endDate");
        }
        if (request.categories() != null) {
            Set<UUID> seen = new HashSet<>();
            BigDecimal allocated = BigDecimal.ZERO;
            for (CategoryAllocation allocation : request.categories()) {
                if (!seen.add(allocation.categoryId())) {
                    throw ApiException.badRequest("INVALID_REQUEST", "Each category can appear only once in a budget");
                }
                categoryService.require(allocation.categoryId());
                allocated = allocated.add(allocation.allocatedAmount());
            }
            if (allocated.compareTo(request.totalAmount()) > 0) {
                throw ApiException.badRequest("INVALID_AMOUNT", "Category allocations exceed the budget total");
            }
        }
        budget.setBudgetName(request.budgetName().trim());
        budget.setBudgetType(type);
        budget.setTotalAmount(request.totalAmount());
        budget.setStartDate(request.startDate());
        budget.setEndDate(end);
        budget.setAlertThresholdPercent(request.alertThreshold() == null ? 90 : request.alertThreshold());
        if (request.isActive() != null) {
            budget.setActive(request.isActive());
        }
    }

    private List<BudgetCategory> saveAllocations(Budget budget, List<CategoryAllocation> allocations) {
        if (allocations == null || allocations.isEmpty()) {
            return List.of();
        }
        return budgetCategoryRepository.saveAll(allocations.stream()
                .map(a -> new BudgetCategory(budget.getId(), a.categoryId(), a.allocatedAmount())).toList());
    }

    private List<BudgetCategory> replaceAllocations(Budget budget, List<CategoryAllocation> allocations) {
        budgetCategoryRepository.deleteByBudgetId(budget.getId());
        return saveAllocations(budget, allocations);
    }

    private Map<UUID, List<BudgetCategory>> allocationsFor(List<Budget> budgets) {
        if (budgets.isEmpty()) {
            return Map.of();
        }
        return budgetCategoryRepository.findByBudgetIdIn(budgets.stream().map(Budget::getId).toList()).stream()
                .collect(Collectors.groupingBy(BudgetCategory::getBudgetId));
    }

    private Budget require(UUID userId, UUID budgetId) {
        return budgetRepository.findByIdAndUserId(budgetId, userId)
                .orElseThrow(() -> ApiException.notFound("Budget not found"));
    }

    private static BudgetAlert alert(BudgetResponse budget, UUID categoryId, String categoryName, BigDecimal limit,
                                     BigDecimal spent, BigDecimal percentage, boolean exceeded) {
        String subject = categoryName == null ? budget.budgetName() : budget.budgetName() + " · " + categoryName;
        String message = exceeded
                ? subject + " is over budget: spent " + spent.toPlainString() + " of " + limit.toPlainString()
                : subject + " has used " + percentage.setScale(0, RoundingMode.HALF_UP) + "% of its budget";
        return new BudgetAlert(budget.id(), budget.budgetName(), categoryId, categoryName, limit, spent, percentage,
                exceeded ? "exceeded" : "warning", message);
    }

    private static Map<String, Object> snapshot(Budget budget) {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("budgetName", budget.getBudgetName());
        values.put("totalAmount", budget.getTotalAmount() == null ? null : budget.getTotalAmount().toPlainString());
        values.put("startDate", String.valueOf(budget.getStartDate()));
        values.put("endDate", String.valueOf(budget.getEndDate()));
        return values;
    }
}

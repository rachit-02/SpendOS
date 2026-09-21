package com.spendos.goals.service;

import com.spendos.analytics.repository.AggregateQueries;
import com.spendos.analytics.repository.AggregateQueries.MonthTotals;
import com.spendos.analytics.service.PeriodResolver;
import com.spendos.audit.service.AuditService;
import com.spendos.common.exception.ApiException;
import com.spendos.common.util.Money;
import com.spendos.common.util.Times;
import com.spendos.goals.domain.FinancialGoal;
import com.spendos.goals.dto.GoalDtos.GoalRequest;
import com.spendos.goals.dto.GoalDtos.GoalResponse;
import com.spendos.goals.repository.FinancialGoalRepository;
import com.spendos.users.service.UserPreferencesService;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Savings goals with a progress projection based on the user's actual average monthly savings. */
@Service
public class GoalService {

    private final FinancialGoalRepository goalRepository;
    private final AggregateQueries aggregates;
    private final UserPreferencesService preferencesService;
    private final PeriodResolver periods;
    private final AuditService auditService;

    public GoalService(FinancialGoalRepository goalRepository, AggregateQueries aggregates,
                       UserPreferencesService preferencesService, PeriodResolver periods, AuditService auditService) {
        this.goalRepository = goalRepository;
        this.aggregates = aggregates;
        this.preferencesService = preferencesService;
        this.periods = periods;
        this.auditService = auditService;
    }

    @Transactional(readOnly = true)
    public List<GoalResponse> list(UUID userId, Boolean active, String sortBy) {
        BigDecimal savings = averageMonthlySavings(userId);
        Comparator<GoalResponse> order = "priority".equals(sortBy)
                ? Comparator.comparing(GoalResponse::progressPercentage).reversed()
                : Comparator.comparing(GoalResponse::targetDate);
        return goalRepository.findByUserId(userId).stream()
                .filter(g -> active == null || g.isActive() == active)
                .map(g -> toResponse(g, savings))
                .sorted(order)
                .toList();
    }

    @Transactional
    public GoalResponse create(UUID userId, GoalRequest request) {
        FinancialGoal goal = new FinancialGoal();
        goal.setUserId(userId);
        goal.setCurrencyCode(preferencesService.currencyFor(userId));
        apply(goal, request, userId);
        FinancialGoal saved = goalRepository.save(goal);
        auditService.record(userId, "goal", saved.getId(), AuditService.CREATE, null,
                Map.of("goalName", saved.getGoalName(), "targetAmount", saved.getTargetAmount().toPlainString()));
        return toResponse(saved, averageMonthlySavings(userId));
    }

    @Transactional
    public GoalResponse update(UUID userId, UUID goalId, GoalRequest request) {
        FinancialGoal goal = require(userId, goalId);
        apply(goal, request, userId);
        auditService.record(userId, "goal", goalId, AuditService.UPDATE, null, Map.of("goalName", goal.getGoalName()));
        return toResponse(goalRepository.save(goal), averageMonthlySavings(userId));
    }

    /** Adds (or, with a negative amount, withdraws) money set aside for the goal. */
    @Transactional
    public GoalResponse contribute(UUID userId, UUID goalId, BigDecimal amount) {
        FinancialGoal goal = require(userId, goalId);
        BigDecimal progress = Money.nz(goal.getCurrentProgress()).add(amount);
        if (progress.signum() < 0) {
            throw ApiException.badRequest("INVALID_AMOUNT", "Progress cannot go below zero");
        }
        goal.setCurrentProgress(progress);
        auditService.record(userId, "goal", goalId, AuditService.UPDATE, null, Map.of("contribution", amount.toPlainString()));
        return toResponse(goalRepository.save(goal), averageMonthlySavings(userId));
    }

    @Transactional
    public void delete(UUID userId, UUID goalId) {
        FinancialGoal goal = require(userId, goalId);
        goalRepository.delete(goal);
        auditService.record(userId, "goal", goalId, AuditService.DELETE, Map.of("goalName", goal.getGoalName()), null);
    }

    /** Sum of what active goals need per month to finish on time (used by affordability). */
    @Transactional(readOnly = true)
    public BigDecimal monthlyContributionsNeeded(UUID userId) {
        return list(userId, true, null).stream().map(GoalResponse::monthlyContributionNeeded)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /** Average monthly net savings over the last three complete months with activity. */
    @Transactional(readOnly = true)
    public BigDecimal averageMonthlySavings(UUID userId) {
        YearMonth current = YearMonth.from(periods.today(userId));
        List<MonthTotals> months = aggregates.monthlyTotals(userId, current.minusMonths(3), current.minusMonths(1)).stream()
                .filter(m -> m.income().signum() != 0 || m.expense().signum() != 0).toList();
        if (months.isEmpty()) {
            return BigDecimal.ZERO;
        }
        return months.stream().map(MonthTotals::net).reduce(BigDecimal.ZERO, BigDecimal::add)
                .divide(BigDecimal.valueOf(months.size()), 2, RoundingMode.HALF_UP);
    }

    /** Whole months needed to save {@code remaining} at {@code perMonth}; null if never. */
    public static Integer monthsNeeded(BigDecimal remaining, BigDecimal perMonth) {
        if (remaining.signum() <= 0) {
            return 0;
        }
        if (perMonth == null || perMonth.signum() <= 0) {
            return null;
        }
        return remaining.divide(perMonth, 0, RoundingMode.CEILING).intValue();
    }

    GoalResponse toResponse(FinancialGoal goal, BigDecimal averageSavings) {
        LocalDate today = periods.today(goal.getUserId());
        BigDecimal progress = Money.nz(goal.getCurrentProgress());
        BigDecimal remaining = goal.getTargetAmount().subtract(progress).max(BigDecimal.ZERO);
        long monthsLeft = Math.max(1, ChronoUnit.MONTHS.between(YearMonth.from(today), YearMonth.from(goal.getTargetDate())));
        BigDecimal needed = remaining.divide(BigDecimal.valueOf(monthsLeft), 2, RoundingMode.HALF_UP);
        Integer months = monthsNeeded(remaining, averageSavings);
        LocalDate projected = months == null ? null : today.plusMonths(months);
        boolean onTrack = remaining.signum() == 0 || (projected != null && !projected.isAfter(goal.getTargetDate()));
        return new GoalResponse(goal.getId(), goal.getGoalName(), goal.getGoalDescription(), goal.getGoalType(),
                Money.scale(goal.getTargetAmount()), Money.scale(progress), Money.scale(remaining),
                Money.percent(progress, goal.getTargetAmount()), goal.getTargetDate(), goal.isActive(), months, needed,
                projected, onTrack, goal.getCurrencyCode(), Times.utc(goal.getCreatedAt()));
    }

    private void apply(FinancialGoal goal, GoalRequest request, UUID userId) {
        if (!request.targetDate().isAfter(periods.today(userId))) {
            throw ApiException.badRequest("INVALID_REQUEST", "targetDate must be in the future");
        }
        goal.setGoalName(request.goalName().trim());
        goal.setGoalDescription(request.goalDescription());
        goal.setGoalType(request.goalType() == null ? "savings" : request.goalType());
        goal.setTargetAmount(request.targetAmount());
        if (request.currentProgress() != null) {
            goal.setCurrentProgress(request.currentProgress());
        }
        goal.setTargetDate(request.targetDate());
        if (request.isActive() != null) {
            goal.setActive(request.isActive());
        }
    }

    private FinancialGoal require(UUID userId, UUID goalId) {
        return goalRepository.findByIdAndUserId(goalId, userId).orElseThrow(() -> ApiException.notFound("Goal not found"));
    }
}

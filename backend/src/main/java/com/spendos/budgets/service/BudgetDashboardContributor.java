package com.spendos.budgets.service;

import com.spendos.analytics.dto.DashboardResponse;
import com.spendos.analytics.dto.DashboardResponse.BudgetPreview;
import com.spendos.analytics.service.DashboardSectionContributor;
import java.time.YearMonth;
import java.util.UUID;
import org.springframework.stereotype.Component;

/** Adds active budgets overlapping the dashboard month to the dashboard. */
@Component
public class BudgetDashboardContributor implements DashboardSectionContributor {

    private final BudgetService budgetService;

    public BudgetDashboardContributor(BudgetService budgetService) {
        this.budgetService = budgetService;
    }

    @Override
    public void contribute(UUID userId, YearMonth month, DashboardResponse dashboard) {
        budgetService.list(userId, true).stream()
                .filter(b -> !b.startDate().isAfter(month.atEndOfMonth()) && !b.endDate().isBefore(month.atDay(1)))
                .limit(6)
                .forEach(b -> dashboard.budgets().add(new BudgetPreview(b.id(), b.budgetName(), b.totalAmount(),
                        b.spentAmount(), b.percentage(), b.remainingAmount(), b.isExceeded(), b.isAlert(),
                        b.alertThreshold())));
    }
}

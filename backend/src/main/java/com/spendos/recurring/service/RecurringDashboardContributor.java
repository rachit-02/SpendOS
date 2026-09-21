package com.spendos.recurring.service;

import com.spendos.analytics.dto.DashboardResponse;
import com.spendos.analytics.dto.DashboardResponse.RecurringPreview;
import com.spendos.analytics.service.DashboardSectionContributor;
import java.time.YearMonth;
import java.util.UUID;
import org.springframework.stereotype.Component;

/** Adds the next few expected recurring payments to the dashboard. */
@Component
public class RecurringDashboardContributor implements DashboardSectionContributor {

    private final RecurringService recurringService;

    public RecurringDashboardContributor(RecurringService recurringService) {
        this.recurringService = recurringService;
    }

    @Override
    public void contribute(UUID userId, YearMonth month, DashboardResponse dashboard) {
        recurringService.list(userId, "all", "nextDate").stream()
                .filter(r -> r.isActive())
                .limit(5)
                .forEach(r -> dashboard.recurringPayments().add(new RecurringPreview(r.id(), r.merchantName(),
                        r.typicalAmount(), r.frequency(), r.nextExpectedDate(), r.isUserConfirmed())));
    }
}

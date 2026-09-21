package com.spendos.insights.service;

import com.spendos.analytics.dto.DashboardResponse;
import com.spendos.analytics.dto.DashboardResponse.InsightPreview;
import com.spendos.analytics.service.DashboardSectionContributor;
import java.time.YearMonth;
import java.util.UUID;
import org.springframework.stereotype.Component;

/** Adds the month's three most important insights to the dashboard. */
@Component
public class InsightDashboardContributor implements DashboardSectionContributor {

    private final InsightService insightService;

    public InsightDashboardContributor(InsightService insightService) {
        this.insightService = insightService;
    }

    @Override
    public void contribute(UUID userId, YearMonth month, DashboardResponse dashboard) {
        insightService.fresh(userId, month).stream().limit(3)
                .forEach(i -> dashboard.insights().add(new InsightPreview(i.getId(), i.getInsightType(), i.getTitle(),
                        i.getDescription(), i.getImpactValue(), i.isActionable())));
    }
}

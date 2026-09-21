package com.spendos.analytics.service;

import com.spendos.analytics.dto.DashboardResponse;
import java.time.YearMonth;
import java.util.UUID;

/**
 * Lets other modules (budgets, recurring payments, insights) add their preview section to the
 * dashboard without the analytics module depending on them.
 */
public interface DashboardSectionContributor {

    void contribute(UUID userId, YearMonth month, DashboardResponse dashboard);
}

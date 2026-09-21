package com.spendos.analytics.service;

import com.spendos.common.exception.ApiException;
import com.spendos.users.service.UserPreferencesService;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.UUID;
import org.springframework.stereotype.Component;

/** Resolves optional month/year parameters to a month, defaulting to the current month in the user's timezone. */
@Component
public class PeriodResolver {

    private final UserPreferencesService preferencesService;

    public PeriodResolver(UserPreferencesService preferencesService) {
        this.preferencesService = preferencesService;
    }

    public LocalDate today(UUID userId) {
        return LocalDate.now(preferencesService.zoneFor(userId));
    }

    public YearMonth resolve(UUID userId, Integer month, Integer year) {
        YearMonth current = YearMonth.from(today(userId));
        if (month == null && year == null) {
            return current;
        }
        if (month == null || year == null) {
            throw ApiException.badRequest("INVALID_REQUEST", "month and year must be supplied together");
        }
        if (month < 1 || month > 12 || year < 1990 || year > current.getYear() + 1) {
            throw ApiException.badRequest("INVALID_REQUEST", "Invalid month or year");
        }
        return YearMonth.of(year, month);
    }

    /** Last day to count for the month: today for the current month, otherwise the month's end. */
    public LocalDate effectiveEnd(UUID userId, YearMonth month) {
        LocalDate today = today(userId);
        return month.equals(YearMonth.from(today)) ? today : month.atEndOfMonth();
    }
}

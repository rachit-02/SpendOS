package com.spendos.users.service;

import com.spendos.common.exception.ApiException;
import com.spendos.users.dto.UserDtos.PreferencesResponse;
import com.spendos.users.dto.UserDtos.UpdatePreferencesRequest;
import com.spendos.users.domain.UserPreferences;
import com.spendos.users.repository.UserPreferencesRepository;
import java.time.DateTimeException;
import java.time.ZoneId;
import java.util.Currency;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserPreferencesService {

    private final UserPreferencesRepository repository;

    public UserPreferencesService(UserPreferencesRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public UserPreferences createDefaults(UUID userId) {
        return repository.findByUserId(userId).orElseGet(() -> repository.save(new UserPreferences(userId)));
    }

    /** Returns the user's preferences, creating defaults for users registered before preferences existed. */
    @Transactional
    public UserPreferences getOrCreate(UUID userId) {
        return createDefaults(userId);
    }

    @Transactional
    public PreferencesResponse get(UUID userId) {
        return PreferencesResponse.from(getOrCreate(userId));
    }

    @Transactional
    public PreferencesResponse update(UUID userId, UpdatePreferencesRequest request) {
        UserPreferences preferences = getOrCreate(userId);
        if (request.currencyCode() != null) {
            try {
                Currency.getInstance(request.currencyCode());
            } catch (IllegalArgumentException exception) {
                throw ApiException.badRequest("INVALID_REQUEST", "Unknown currency code");
            }
            preferences.setCurrencyCode(request.currencyCode());
        }
        if (request.timezone() != null) {
            try {
                ZoneId.of(request.timezone());
            } catch (DateTimeException exception) {
                throw ApiException.badRequest("INVALID_REQUEST", "Unknown timezone");
            }
            preferences.setTimezone(request.timezone());
        }
        if (request.fiscalYearStartMonth() != null) {
            preferences.setFiscalYearStartMonth(request.fiscalYearStartMonth());
        }
        if (request.theme() != null) {
            preferences.setTheme(request.theme());
        }
        if (request.language() != null) {
            preferences.setLanguage(request.language());
        }
        if (request.financialHealthScoreEnabled() != null) {
            preferences.setFinancialHealthScoreEnabled(request.financialHealthScoreEnabled());
        }
        if (request.emailReportsEnabled() != null) {
            preferences.setEmailReportsEnabled(request.emailReportsEnabled());
        }
        if (request.emailAlertsEnabled() != null) {
            preferences.setEmailAlertsEnabled(request.emailAlertsEnabled());
        }
        return PreferencesResponse.from(repository.save(preferences));
    }

    @Transactional(readOnly = true)
    public ZoneId zoneFor(UUID userId) {
        return repository.findByUserId(userId).map(p -> {
            try {
                return ZoneId.of(p.getTimezone());
            } catch (DateTimeException | NullPointerException exception) {
                return ZoneId.of("Asia/Kolkata");
            }
        }).orElse(ZoneId.of("Asia/Kolkata"));
    }

    @Transactional(readOnly = true)
    public boolean healthScoreEnabled(UUID userId) {
        return repository.findByUserId(userId).map(UserPreferences::isFinancialHealthScoreEnabled).orElse(true);
    }

    @Transactional(readOnly = true)
    public String currencyFor(UUID userId) {
        return repository.findByUserId(userId).map(UserPreferences::getCurrencyCode).orElse("INR");
    }
}

package com.spendos.users.dto;

import com.spendos.users.entity.UserPreferences;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Request/response records for the users module. */
public final class UserDtos {

    private UserDtos() {
    }

    public record UpdateProfileRequest(
            @Size(min = 1, max = 255) String fullName,
            @Email(message = "Invalid email format") @Size(max = 255) String email) {
    }

    public record ChangePasswordRequest(@NotBlank String currentPassword, @NotBlank @Size(max = 128) String newPassword) {
        @Override
        public String toString() {
            return "ChangePasswordRequest[***]";
        }
    }

    public record DeleteAccountRequest(@NotBlank String confirmPassword) {
        @Override
        public String toString() {
            return "DeleteAccountRequest[***]";
        }
    }

    public record PreferencesResponse(
            String currencyCode, String timezone, Integer fiscalYearStartMonth, String theme, String language,
            boolean financialHealthScoreEnabled, boolean demoMode, boolean emailReportsEnabled,
            boolean emailAlertsEnabled) {

        public static PreferencesResponse from(UserPreferences preferences) {
            return new PreferencesResponse(preferences.getCurrencyCode(), preferences.getTimezone(),
                    preferences.getFiscalYearStartMonth(), preferences.getTheme(), preferences.getLanguage(),
                    preferences.isFinancialHealthScoreEnabled(), preferences.isDemoMode(),
                    preferences.isEmailReportsEnabled(), preferences.isEmailAlertsEnabled());
        }
    }

    /** Partial update: null fields are left unchanged. */
    public record UpdatePreferencesRequest(
            @Pattern(regexp = "^[A-Z]{3}$", message = "currencyCode must be a 3-letter ISO code") String currencyCode,
            @Size(max = 50) String timezone,
            @Min(1) @Max(12) Integer fiscalYearStartMonth,
            @Pattern(regexp = "^(light|dark|system)$", message = "theme must be light, dark or system") String theme,
            @Pattern(regexp = "^[a-z]{2}(-[A-Z]{2})?$", message = "language must be like 'en' or 'en-IN'") String language,
            Boolean financialHealthScoreEnabled,
            Boolean emailReportsEnabled,
            Boolean emailAlertsEnabled) {
    }

    public record MessageResponse(String message) {
    }
}

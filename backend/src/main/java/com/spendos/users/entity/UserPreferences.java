package com.spendos.users.entity;

import com.spendos.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.util.UUID;

@Entity
@Table(name = "user_preferences")
public class UserPreferences extends BaseEntity {

    @Column(name = "user_id", nullable = false, unique = true)
    private UUID userId;

    @Column(name = "currency_code", length = 3)
    private String currencyCode = "INR";

    @Column(name = "timezone", length = 50)
    private String timezone = "Asia/Kolkata";

    @Column(name = "fiscal_year_start_month")
    private Integer fiscalYearStartMonth = 1;

    @Column(name = "theme", length = 50)
    private String theme = "light";

    @Column(name = "language", length = 10)
    private String language = "en";

    @Column(name = "financial_health_score_enabled")
    private boolean financialHealthScoreEnabled = true;

    @Column(name = "email_reports_enabled")
    private boolean emailReportsEnabled;

    @Column(name = "email_alerts_enabled")
    private boolean emailAlertsEnabled;

    @Column(name = "demo_mode")
    private boolean demoMode;

    protected UserPreferences() {
    }

    public UserPreferences(UUID userId) {
        this.userId = userId;
    }

    public UUID getUserId() {
        return userId;
    }

    public String getCurrencyCode() {
        return currencyCode;
    }

    public void setCurrencyCode(String currencyCode) {
        this.currencyCode = currencyCode;
    }

    public String getTimezone() {
        return timezone;
    }

    public void setTimezone(String timezone) {
        this.timezone = timezone;
    }

    public Integer getFiscalYearStartMonth() {
        return fiscalYearStartMonth;
    }

    public void setFiscalYearStartMonth(Integer fiscalYearStartMonth) {
        this.fiscalYearStartMonth = fiscalYearStartMonth;
    }

    public String getTheme() {
        return theme;
    }

    public void setTheme(String theme) {
        this.theme = theme;
    }

    public String getLanguage() {
        return language;
    }

    public void setLanguage(String language) {
        this.language = language;
    }

    public boolean isFinancialHealthScoreEnabled() {
        return financialHealthScoreEnabled;
    }

    public void setFinancialHealthScoreEnabled(boolean financialHealthScoreEnabled) {
        this.financialHealthScoreEnabled = financialHealthScoreEnabled;
    }

    public boolean isEmailReportsEnabled() {
        return emailReportsEnabled;
    }

    public void setEmailReportsEnabled(boolean emailReportsEnabled) {
        this.emailReportsEnabled = emailReportsEnabled;
    }

    public boolean isEmailAlertsEnabled() {
        return emailAlertsEnabled;
    }

    public void setEmailAlertsEnabled(boolean emailAlertsEnabled) {
        this.emailAlertsEnabled = emailAlertsEnabled;
    }

    public boolean isDemoMode() {
        return demoMode;
    }

    public void setDemoMode(boolean demoMode) {
        this.demoMode = demoMode;
    }
}

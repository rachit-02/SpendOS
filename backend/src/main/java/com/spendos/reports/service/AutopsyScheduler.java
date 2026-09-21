package com.spendos.reports.service;

import java.sql.Date;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * On the first of each month, builds the completed month's autopsy for users who had activity in it.
 * Email delivery is optional (email_reports_enabled) and not wired for the MVP; reports are available
 * in the app and as PDF.
 */
@Component
public class AutopsyScheduler {

    private static final Logger log = LoggerFactory.getLogger(AutopsyScheduler.class);

    private final JdbcTemplate jdbc;
    private final AutopsyService autopsyService;

    public AutopsyScheduler(JdbcTemplate jdbc, AutopsyService autopsyService) {
        this.jdbc = jdbc;
        this.autopsyService = autopsyService;
    }

    @Scheduled(cron = "${reports.autopsy-schedule:0 0 7 1 * *}", zone = "Asia/Kolkata")
    public void generateLastMonth() {
        YearMonth month = YearMonth.from(LocalDate.now(ZoneId.of("Asia/Kolkata"))).minusMonths(1);
        List<UUID> users = jdbc.queryForList("""
                SELECT DISTINCT t.user_id FROM transactions t JOIN users u ON u.id = t.user_id
                WHERE t.transaction_date BETWEEN ? AND ? AND u.deleted_at IS NULL AND u.is_active""",
                UUID.class, Date.valueOf(month.atDay(1)), Date.valueOf(month.atEndOfMonth()));
        int failures = 0;
        for (UUID userId : users) {
            try {
                autopsyService.generate(userId, month);
            } catch (RuntimeException exception) {
                failures++;
                log.warn("Autopsy generation failed | userId={} | month={}", userId, month, exception);
            }
        }
        log.info("Monthly autopsies | month={} | users={} | failures={}", month, users.size(), failures);
    }
}

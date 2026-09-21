package com.spendos.insights.service;

import com.spendos.insights.engine.InsightEngine;
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
 * Daily insight generation for recently active users (so insights are ready before they open the app)
 * and removal of expired insights. On the first days of a month the completed previous month is
 * generated too. Only runs when scheduling is enabled.
 */
@Component
public class InsightScheduler {

    private static final Logger log = LoggerFactory.getLogger(InsightScheduler.class);

    private final JdbcTemplate jdbc;
    private final InsightEngine engine;
    private final InsightService insightService;

    public InsightScheduler(JdbcTemplate jdbc, InsightEngine engine, InsightService insightService) {
        this.jdbc = jdbc;
        this.engine = engine;
        this.insightService = insightService;
    }

    @Scheduled(cron = "${insights.schedule:0 30 5 * * *}", zone = "Asia/Kolkata")
    public void generateDaily() {
        LocalDate today = LocalDate.now(ZoneId.of("Asia/Kolkata"));
        List<UUID> users = jdbc.queryForList("""
                SELECT DISTINCT t.user_id FROM transactions t JOIN users u ON u.id = t.user_id
                WHERE t.transaction_date >= ? AND u.deleted_at IS NULL AND u.is_active""",
                UUID.class, Date.valueOf(today.minusDays(45)));
        int failures = 0;
        for (UUID userId : users) {
            try {
                engine.generate(userId, YearMonth.from(today));
                if (today.getDayOfMonth() <= 3) {
                    engine.generate(userId, YearMonth.from(today).minusMonths(1));
                }
            } catch (RuntimeException exception) {
                failures++;
                log.warn("Insight generation failed | userId={}", userId, exception);
            }
        }
        int purged = insightService.purgeExpired();
        log.info("Daily insights | users={} | failures={} | expiredRemoved={}", users.size(), failures, purged);
    }
}

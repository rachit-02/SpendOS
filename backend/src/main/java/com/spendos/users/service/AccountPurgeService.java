package com.spendos.users.service;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Permanently removes accounts deleted more than 30 days ago (SECURITY.md "Account Deletion").
 * Everything owned by the user is removed; audit log rows are kept for compliance with their user
 * reference cleared (ON DELETE SET NULL). Merchants that were created only from this user's
 * statements, and are no longer used by anyone, are removed too so their names do not linger.
 */
@Service
public class AccountPurgeService {

    static final Duration RETENTION = Duration.ofDays(30);
    private static final Logger log = LoggerFactory.getLogger(AccountPurgeService.class);

    private final JdbcTemplate jdbc;

    public AccountPurgeService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Scheduled(cron = "${users.purge-schedule:0 30 3 * * *}")
    public void scheduledPurge() {
        purge(LocalDateTime.now(ZoneOffset.UTC));
    }

    /** @return how many accounts were purged */
    @Transactional
    public int purge(LocalDateTime nowUtc) {
        List<UUID> due = jdbc.queryForList(
                "SELECT id FROM users WHERE deleted_at IS NOT NULL AND deleted_at < ?", UUID.class,
                Timestamp.valueOf(nowUtc.minus(RETENTION)));
        for (UUID userId : due) {
            // Transactions first: they reference accounts with ON DELETE RESTRICT.
            jdbc.update("DELETE FROM transactions WHERE user_id = ?", userId);
            jdbc.update("DELETE FROM users WHERE id = ?", userId); // cascades to all other user data
        }
        int merchants = due.isEmpty() ? 0 : jdbc.update("""
                DELETE FROM merchants m
                WHERE NOT m.is_verified
                  AND NOT EXISTS (SELECT 1 FROM transactions t WHERE t.merchant_id = m.id)
                  AND NOT EXISTS (SELECT 1 FROM user_merchant_mappings u WHERE u.normalized_merchant_id = m.id)
                  AND NOT EXISTS (SELECT 1 FROM recurring_payments r WHERE r.merchant_id = m.id)
                  AND NOT EXISTS (SELECT 1 FROM insights i WHERE i.related_merchant_id = m.id)
                  AND NOT EXISTS (SELECT 1 FROM merchant_normalization_rules n WHERE n.target_merchant_id = m.id)""");
        if (!due.isEmpty()) {
            log.info("Purged deleted accounts | accounts={} | orphanMerchants={}", due.size(), merchants);
        }
        return due.size();
    }
}

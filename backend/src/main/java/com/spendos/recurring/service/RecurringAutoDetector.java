package com.spendos.recurring.service;

import com.spendos.transactions.service.TransactionsChangedEvent;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Re-runs recurring detection in the background after a user's transactions change (import, edit),
 * at most once per minute per user. Disabled in tests, which trigger detection explicitly.
 */
@Component
@ConditionalOnProperty(name = "app.recurring.auto-detect", havingValue = "true", matchIfMissing = true)
public class RecurringAutoDetector {

    private static final Logger log = LoggerFactory.getLogger(RecurringAutoDetector.class);
    private static final long MIN_INTERVAL_MS = 60_000;

    private final RecurringService recurringService;
    private final Map<UUID, Long> lastRun = new ConcurrentHashMap<>();

    public RecurringAutoDetector(RecurringService recurringService) {
        this.recurringService = recurringService;
    }

    @Async("importExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onTransactionsChanged(TransactionsChangedEvent event) {
        long now = System.currentTimeMillis();
        Long previous = lastRun.put(event.userId(), now);
        if (previous != null && now - previous < MIN_INTERVAL_MS) {
            return;
        }
        try {
            recurringService.detect(event.userId());
        } catch (RuntimeException exception) {
            log.warn("Background recurring detection failed | userId={}", event.userId(), exception);
        }
    }
}

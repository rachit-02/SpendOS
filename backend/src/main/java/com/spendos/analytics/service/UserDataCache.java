package com.spendos.analytics.service;

import com.spendos.transactions.service.TransactionsChangedEvent;
import java.time.Clock;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Short-lived per-user cache for computed views (dashboard, monthly analytics). Entries expire after a
 * TTL and all of a user's entries are dropped whenever their data changes, so results are never stale
 * after an edit. In-memory: a multi-instance deployment should swap this for Redis.
 */
@Component
public class UserDataCache {

    private static final Duration TTL = Duration.ofMinutes(5);

    private record Entry(Object value, long expiresAt) {
    }

    private final Map<UUID, Map<String, Entry>> entries = new ConcurrentHashMap<>();
    private final Clock clock = Clock.systemUTC();

    @SuppressWarnings("unchecked")
    public <T> T get(UUID userId, String key, Supplier<T> loader) {
        long now = clock.millis();
        Map<String, Entry> userEntries = entries.computeIfAbsent(userId, id -> new ConcurrentHashMap<>());
        Entry entry = userEntries.get(key);
        if (entry != null && entry.expiresAt() > now) {
            return (T) entry.value();
        }
        T value = loader.get();
        userEntries.put(key, new Entry(value, now + TTL.toMillis()));
        return value;
    }

    public void evict(UUID userId) {
        entries.remove(userId);
    }

    @EventListener
    public void onTransactionsChanged(TransactionsChangedEvent event) {
        evict(event.userId());
    }
}

package com.spendos.common.security;

import java.time.Clock;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * In-memory fixed-window rate limiter. Adequate for a single-instance MVP; a multi-instance
 * deployment should move the counters to Redis behind the same interface.
 */
@Component
public class RateLimiter {

    private final Map<String, Window> windows = new ConcurrentHashMap<>();
    private final Clock clock;

    public RateLimiter() {
        this(Clock.systemUTC());
    }

    RateLimiter(Clock clock) {
        this.clock = clock;
    }

    /** Result of an acquisition attempt; {@code retryAfterSeconds} is set when rejected. */
    public record Decision(boolean allowed, long retryAfterSeconds) {
    }

    public Decision tryAcquire(String key, int limit, Duration window) {
        long now = clock.millis();
        long windowMillis = window.toMillis();
        Window current = windows.compute(key, (k, existing) -> {
            if (existing == null || now - existing.start >= windowMillis) {
                return new Window(now, windowMillis, 1);
            }
            return new Window(existing.start, windowMillis, existing.count + 1);
        });
        if (current.count <= limit) {
            return new Decision(true, 0);
        }
        long retryAfter = Math.max(1, (current.start + windowMillis - now + 999) / 1000);
        return new Decision(false, retryAfter);
    }

    /** Drops expired windows so the map does not grow without bound. */
    @Scheduled(fixedDelay = 600_000)
    public void purgeExpired() {
        long now = clock.millis();
        windows.entrySet().removeIf(entry -> now - entry.getValue().start >= entry.getValue().length);
    }

    public void reset() {
        windows.clear();
    }

    private record Window(long start, long length, int count) {
    }
}

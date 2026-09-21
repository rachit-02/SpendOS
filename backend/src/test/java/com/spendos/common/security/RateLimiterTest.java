package com.spendos.common.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;

class RateLimiterTest {

    private final AtomicLong now = new AtomicLong(1_000_000);
    private final Clock clock = new Clock() {
        @Override
        public java.time.ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(java.time.ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return Instant.ofEpochMilli(now.get());
        }
    };
    private final RateLimiter limiter = new RateLimiter(clock);

    @Test
    void allowsUpToLimitThenRejectsWithRetryAfter() {
        for (int i = 0; i < 5; i++) {
            assertThat(limiter.tryAcquire("login|ip:1", 5, Duration.ofMinutes(15)).allowed()).isTrue();
        }
        RateLimiter.Decision rejected = limiter.tryAcquire("login|ip:1", 5, Duration.ofMinutes(15));
        assertThat(rejected.allowed()).isFalse();
        assertThat(rejected.retryAfterSeconds()).isEqualTo(900);
    }

    @Test
    void windowResetsAfterItElapses() {
        for (int i = 0; i < 6; i++) {
            limiter.tryAcquire("k", 5, Duration.ofMinutes(1));
        }
        now.addAndGet(Duration.ofMinutes(1).toMillis());
        assertThat(limiter.tryAcquire("k", 5, Duration.ofMinutes(1)).allowed()).isTrue();
    }

    @Test
    void keysAreIndependent() {
        for (int i = 0; i < 5; i++) {
            limiter.tryAcquire("a", 5, Duration.ofMinutes(1));
        }
        assertThat(limiter.tryAcquire("a", 5, Duration.ofMinutes(1)).allowed()).isFalse();
        assertThat(limiter.tryAcquire("b", 5, Duration.ofMinutes(1)).allowed()).isTrue();
    }

    @Test
    void purgeRemovesExpiredWindows() {
        limiter.tryAcquire("a", 1, Duration.ofSeconds(1));
        limiter.tryAcquire("a", 1, Duration.ofSeconds(1));
        now.addAndGet(2000);
        limiter.purgeExpired();
        assertThat(limiter.tryAcquire("a", 1, Duration.ofSeconds(1)).allowed()).isTrue();
    }
}

package com.spendos.common.util;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

/** Entity timestamps are UTC LocalDateTimes; the API exposes them as ISO-8601 instants ("...Z"). */
public final class Times {

    private Times() {
    }

    public static Instant utc(LocalDateTime value) {
        return value == null ? null : value.toInstant(ZoneOffset.UTC);
    }

    public static LocalDateTime nowUtc() {
        return LocalDateTime.now(ZoneOffset.UTC);
    }
}

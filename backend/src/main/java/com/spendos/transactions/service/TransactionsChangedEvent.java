package com.spendos.transactions.service;

import java.util.UUID;

/** Published whenever a user's transactions change, so caches (dashboard, analytics) can be evicted. */
public record TransactionsChangedEvent(UUID userId) {
}

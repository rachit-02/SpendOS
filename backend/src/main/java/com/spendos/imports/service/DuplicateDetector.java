package com.spendos.imports.service;

import com.spendos.imports.validator.StatementRowValidator.Candidate;
import com.spendos.merchants.normalizer.MerchantTextCleaner;
import com.spendos.transactions.domain.Transaction;
import java.math.BigDecimal;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Detects rows that already exist for the user. A row is a duplicate of an existing transaction when:
 * <ul>
 *   <li>exact: both carry the same bank reference, or</li>
 *   <li>similar: same type and amount, same normalized merchant text, and dates at most one day apart.</li>
 * </ul>
 * Each existing transaction absorbs at most one row, so two identical coffees on the same day in a
 * statement are kept when only one was previously imported. Rows within one file are never compared
 * against each other: the statement itself is authoritative.
 */
public final class DuplicateDetector {

    public record Match(Candidate candidate, UUID existingId, boolean exact) {
    }

    public record Result(List<Candidate> unique, List<Match> duplicates) {
    }

    private DuplicateDetector() {
    }

    public static Result detect(List<Candidate> candidates, List<Transaction> existing) {
        return detect(candidates, existing, DuplicateDetector::merchantKey);
    }

    /**
     * @param merchantKey maps statement text to a merchant identity; the import pipeline passes the
     *                    rule-based normalizer so "SWIGGY ORDER" and "UPI-SWIGGY-123@ybl" compare equal.
     */
    public static Result detect(List<Candidate> candidates, List<Transaction> existing,
                                Function<String, String> merchantKey) {
        Map<String, String> keyCache = new HashMap<>();
        Function<String, String> key = text -> keyCache.computeIfAbsent(text == null ? "" : text, merchantKey);
        // Index by amount so each row only scans transactions of the same value.
        Map<BigDecimal, List<Transaction>> byAmount = existing.stream()
                .collect(Collectors.groupingBy(t -> t.getAmount().stripTrailingZeros()));
        Set<UUID> consumed = new HashSet<>();
        List<Candidate> unique = new ArrayList<>();
        List<Match> duplicates = new ArrayList<>();
        for (Candidate candidate : candidates) {
            Transaction match = findMatch(candidate,
                    byAmount.getOrDefault(candidate.amount().stripTrailingZeros(), List.of()), consumed, key);
            if (match == null) {
                unique.add(candidate);
            } else {
                consumed.add(match.getId());
                boolean exact = candidate.reference() != null && candidate.reference().equals(match.getExternalReference());
                duplicates.add(new Match(candidate, match.getId(), exact));
            }
        }
        return new Result(unique, duplicates);
    }

    private static Transaction findMatch(Candidate candidate, List<Transaction> existing, Set<UUID> consumed,
                                         Function<String, String> merchantKey) {
        String key = merchantKey.apply(candidate.description());
        Transaction similar = null;
        for (Transaction transaction : existing) {
            if (consumed.contains(transaction.getId())
                    || transaction.getAmount().compareTo(candidate.amount()) != 0
                    || !Objects.equals(transaction.getTransactionType(), candidate.type())) {
                continue;
            }
            if (candidate.reference() != null && candidate.reference().equals(transaction.getExternalReference())) {
                return transaction;
            }
            long days = Math.abs(ChronoUnit.DAYS.between(transaction.getTransactionDate(), candidate.date()));
            if (days <= 1 && similar == null && key.equals(merchantKey.apply(transaction.getRawDescription()))) {
                similar = transaction;
            }
        }
        return similar;
    }

    static String merchantKey(String text) {
        return MerchantTextCleaner.clean(text).toLowerCase(Locale.ROOT);
    }
}

package com.spendos.imports.service;

import com.spendos.imports.validator.StatementRowValidator.Candidate;
import com.spendos.merchants.normalizer.Similarity;
import com.spendos.transactions.domain.Transaction;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Marks rows that only move the account holder's own money as transfers, so they are not counted as
 * income on the way in and spending on the way out.
 *
 * <p>Wallet and UPI statements (Google Pay, PhonePe, Paytm) name the counterparty in the narration, so a
 * row whose counterparty is the account holder moves their own money: "Received from Mr TEST CUSTOMER"
 * is a top-up, not income. The transfer type is what the rest of the pipeline already understands, so a
 * marked row is categorized as Transfers, stored with {@code is_transfer}, and left out of the
 * income and spending aggregates, which all filter on debit or credit.
 */
public final class SelfTransferDetector {

    /** Narrations name the counterparty after one of these. */
    private static final Pattern COUNTERPARTY = Pattern.compile(
            "(?i)\\b(?:received from|money received from|credited by|paid to|sent to|payment to|debited by)\\s+(.+)$");

    /** Said outright by some banks, whoever the counterparty is. */
    private static final Pattern SAYS_SELF = Pattern.compile("(?i)\\b(self transfer|own account|self a/c)\\b");

    /** Titles and honorifics that a statement adds to a name but a profile does not. */
    private static final Set<String> TITLES = Set.of("mr", "mrs", "ms", "miss", "dr", "shri", "smt", "sri", "md");

    /**
     * The other leg of a transfer names an account rather than a payee: a bare number ("2657"), or a
     * bank, card or account reference. A named shop is not a transfer, however well the amount matches.
     */
    private static final Pattern ACCOUNT_REFERENCE = Pattern.compile(
            "(?i)^(?:\\d[\\d\\s-]*|(?:.*\\b(?:bank|a/c|account|card|credit card|wallet|xx+\\d+)\\b.*))$");

    /** How far apart the two legs of one transfer may be dated. */
    private static final int PAIR_WINDOW_DAYS = 1;

    private SelfTransferDetector() {
    }

    /**
     * Returns the candidates with self-transfers retyped as transfers. {@code accountHolder} is the
     * user's own name; without one only narrations that say "self transfer" outright are marked.
     */
    public static List<Candidate> mark(List<Candidate> candidates, String accountHolder) {
        if (candidates.isEmpty()) {
            return candidates;
        }
        Set<String> holderTokens = tokens(accountHolder);
        List<Candidate> marked = new ArrayList<>(candidates);
        boolean[] isTransfer = new boolean[marked.size()];

        for (int i = 0; i < marked.size(); i++) {
            if (movesOwnMoney(marked.get(i).description(), holderTokens)) {
                isTransfer[i] = true;
            }
        }
        // The money came from the holder's own account and went straight out again, or the other way
        // round. Only the leg that names an account rather than a payee is paired, so that spending of
        // the same amount on the same day is not swallowed.
        for (int i = 0; i < marked.size(); i++) {
            if (!isTransfer[i]) {
                continue;
            }
            int other = oppositeLeg(marked, isTransfer, i);
            if (other >= 0) {
                isTransfer[other] = true;
            }
        }
        for (int i = 0; i < marked.size(); i++) {
            if (isTransfer[i] && !Transaction.TRANSFER.equals(marked.get(i).type())) {
                marked.set(i, retype(marked.get(i)));
            }
        }
        return marked;
    }

    /** True when the narration's counterparty is the account holder, or it says so outright. */
    static boolean movesOwnMoney(String description, Set<String> holderTokens) {
        if (description == null || description.isBlank()) {
            return false;
        }
        if (SAYS_SELF.matcher(description).find()) {
            return true;
        }
        if (holderTokens.isEmpty()) {
            return false;
        }
        String counterparty = counterparty(description);
        if (counterparty == null) {
            return false;
        }
        Set<String> theirs = tokens(counterparty);
        if (theirs.isEmpty()) {
            return false;
        }
        // A single-token name ("Rachit") is too common to match on loosely: require the whole name.
        if (holderTokens.size() == 1) {
            return theirs.equals(holderTokens);
        }
        if (theirs.containsAll(holderTokens)) {
            return true;
        }
        return Similarity.ratio(String.join(" ", holderTokens), String.join(" ", theirs)) >= 0.9;
    }

    /** The index of the opposite leg of the same transfer, or -1. */
    private static int oppositeLeg(List<Candidate> candidates, boolean[] isTransfer, int leg) {
        Candidate self = candidates.get(leg);
        for (int i = 0; i < candidates.size(); i++) {
            Candidate other = candidates.get(i);
            if (i == leg || isTransfer[i] || other.type().equals(self.type())) {
                continue;
            }
            if (other.amount().compareTo(self.amount()) != 0
                    || Math.abs(ChronoUnit.DAYS.between(self.date(), other.date())) > PAIR_WINDOW_DAYS) {
                continue;
            }
            String counterparty = counterparty(other.description());
            if (counterparty != null && ACCOUNT_REFERENCE.matcher(counterparty.trim()).matches()) {
                return i;
            }
        }
        return -1;
    }

    private static String counterparty(String description) {
        Matcher matcher = COUNTERPARTY.matcher(description);
        return matcher.find() ? matcher.group(1) : null;
    }

    /** A name reduced to comparable words: lower case, no punctuation, no titles. */
    static Set<String> tokens(String name) {
        if (name == null || name.isBlank()) {
            return Set.of();
        }
        Set<String> tokens = new LinkedHashSet<>();
        for (String word : name.toLowerCase(Locale.ROOT).replaceAll("[^a-z\\s]", " ").split("\\s+")) {
            if (!word.isBlank() && !TITLES.contains(word)) {
                tokens.add(word);
            }
        }
        return tokens;
    }

    private static Candidate retype(Candidate candidate) {
        return new Candidate(candidate.rowNumber(), candidate.rawLine(), candidate.date(), candidate.amount(),
                Transaction.TRANSFER, candidate.description(), candidate.reference(), candidate.paymentMethod());
    }
}

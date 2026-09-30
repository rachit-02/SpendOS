package com.spendos.imports.validator;

import com.spendos.imports.parser.AmountParser;
import com.spendos.imports.parser.AmountParser.ParsedAmount;
import com.spendos.imports.parser.ColumnMapping;
import com.spendos.imports.parser.CsvStatementParser.RawRow;
import com.spendos.imports.parser.DateParser;
import com.spendos.transactions.domain.Transaction;
import com.spendos.transactions.service.TransactionService;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Validates raw rows and converts them into import candidates. Invalid rows are collected with an
 * error code instead of failing the import (row-level error handling from PRODUCT_SPEC.md).
 */
public final class StatementRowValidator {

    public static final BigDecimal MAX_AMOUNT = new BigDecimal("99999999.99");

    /** A row that passed validation. {@code amount} is always positive. */
    public record Candidate(int rowNumber, String rawLine, LocalDate date, BigDecimal amount, String type,
                            String description, String reference, String paymentMethod) {
    }

    public record RowError(int rowNumber, String rawLine, String code, String message) {
    }

    public record Result(List<Candidate> candidates, List<RowError> errors, DateParser.Order dateOrder) {
    }

    private static final Pattern DEBIT_WORDS = Pattern.compile("(?i)^(dr|d|debit|withdrawal|w|out|expense|debit card)$");
    private static final Pattern CREDIT_WORDS = Pattern.compile("(?i)^(cr|c|credit|deposit|in|income|refund)$");
    // Wallet and UPI statements (Google Pay, PhonePe, Paytm) have one unsigned amount column and say which
    // way the money went in the narration instead.
    private static final Pattern CREDIT_NARRATION =
            Pattern.compile("(?i)\\b(received from|money received|credited by|refund received)\\b");
    private static final Pattern DEBIT_NARRATION =
            Pattern.compile("(?i)\\b(paid to|sent to|payment to|debited by)\\b");

    private StatementRowValidator() {
    }

    public static Result validate(List<RawRow> rows, ColumnMapping mapping, DateParser.Order hint, LocalDate today) {
        DateParser.Order order = DateParser.detectOrder(
                rows.stream().limit(500).map(row -> row.cell(mapping.date())).toList(), hint);
        boolean fileHasNegatives = mapping.amount() >= 0 && mapping.type() < 0 && rows.stream()
                .map(row -> AmountParser.parse(row.cell(mapping.amount())))
                .anyMatch(parsed -> parsed != null && parsed.value().signum() < 0);

        List<Candidate> candidates = new ArrayList<>();
        List<RowError> errors = new ArrayList<>();
        for (RawRow row : rows) {
            try {
                candidates.add(toCandidate(row, mapping, order, today, fileHasNegatives));
            } catch (RowRejected rejected) {
                errors.add(new RowError(row.rowNumber(), truncate(row.rawLine()), rejected.code, rejected.getMessage()));
            }
        }
        return new Result(candidates, errors, order);
    }

    private static Candidate toCandidate(RawRow row, ColumnMapping mapping, DateParser.Order order, LocalDate today,
                                         boolean fileHasNegatives) {
        String dateText = row.cell(mapping.date());
        if (dateText == null || dateText.isBlank()) {
            throw new RowRejected("MISSING_DATE", "Missing transaction date");
        }
        LocalDate date = DateParser.parse(dateText, order);
        if (date == null) {
            throw new RowRejected("INVALID_DATE", "Unrecognized date '" + truncate(dateText, 40) + "'");
        }
        if (date.isAfter(today)) {
            throw new RowRejected("INVALID_DATE", "Transaction date is in the future");
        }
        if (date.isBefore(TransactionService.EARLIEST_DATE)) {
            throw new RowRejected("INVALID_DATE", "Transaction date is too far in the past");
        }

        String description = row.cell(mapping.description());
        if (description == null || description.isBlank()) {
            throw new RowRejected("MISSING_DESCRIPTION", "Missing description/merchant");
        }

        String type;
        BigDecimal amount;
        if (mapping.debit() >= 0 || mapping.credit() >= 0) {
            ParsedAmount debit = AmountParser.parse(row.cell(mapping.debit()));
            ParsedAmount credit = AmountParser.parse(row.cell(mapping.credit()));
            boolean hasDebit = debit != null && debit.value().signum() != 0;
            boolean hasCredit = credit != null && credit.value().signum() != 0;
            if (!hasDebit && !hasCredit) {
                ParsedAmount single = AmountParser.parse(row.cell(mapping.amount()));
                if (single == null || single.value().signum() == 0) {
                    throw new RowRejected(invalidOrMissing(row, mapping), "Missing or zero amount");
                }
                type = resolveType(row, mapping, single, fileHasNegatives);
                amount = single.value().abs();
            } else if (hasDebit && hasCredit) {
                throw new RowRejected("INVALID_AMOUNT", "Row has both a debit and a credit amount");
            } else {
                type = hasDebit ? Transaction.DEBIT : Transaction.CREDIT;
                amount = (hasDebit ? debit : credit).value().abs();
            }
        } else {
            String amountText = row.cell(mapping.amount());
            ParsedAmount parsed = AmountParser.parse(amountText);
            if (parsed == null) {
                throw new RowRejected(amountText == null || amountText.isBlank() ? "MISSING_AMOUNT" : "INVALID_AMOUNT",
                        amountText == null || amountText.isBlank() ? "Missing amount" : "Unrecognized amount");
            }
            if (parsed.value().signum() == 0) {
                throw new RowRejected("INVALID_AMOUNT", "Amount must be non-zero");
            }
            type = resolveType(row, mapping, parsed, fileHasNegatives);
            amount = parsed.value().abs();
        }
        if (amount.scale() > 2) {
            amount = amount.setScale(2, java.math.RoundingMode.HALF_UP);
        }
        if (amount.compareTo(MAX_AMOUNT) > 0) {
            throw new RowRejected("INVALID_AMOUNT", "Amount exceeds maximum allowed");
        }
        if (amount.signum() == 0) {
            throw new RowRejected("INVALID_AMOUNT", "Amount must be non-zero");
        }

        String reference = row.cell(mapping.reference());
        String method = paymentMethod(row.cell(mapping.paymentMethod()), description);
        return new Candidate(row.rowNumber(), truncate(row.rawLine()), date, amount, type, description.trim(),
                reference == null || reference.isBlank() ? null : truncate(reference, 255), method);
    }

    /** Type column wins, then a Dr/Cr marker on the amount, then the sign convention of the file. */
    private static String resolveType(RawRow row, ColumnMapping mapping, ParsedAmount parsed, boolean fileHasNegatives) {
        String typeText = row.cell(mapping.type());
        if (typeText != null && !typeText.isBlank()) {
            if (DEBIT_WORDS.matcher(typeText.trim()).matches()) {
                return Transaction.DEBIT;
            }
            if (CREDIT_WORDS.matcher(typeText.trim()).matches()) {
                return Transaction.CREDIT;
            }
        }
        if (parsed.marker() != null) {
            return parsed.marker();
        }
        if (parsed.value().signum() < 0) {
            return Transaction.DEBIT;
        }
        // Signed statements list spending as negative, so a positive amount there is income and the
        // file's own convention has already answered.
        if (fileHasNegatives) {
            return Transaction.CREDIT;
        }
        // Amounts are unsigned, so read the narration. Credit first: a wallet credit reads
        // "Received from <someone>" and then names the account it was paid to.
        String description = row.cell(mapping.description());
        if (description != null && !description.isBlank()) {
            if (CREDIT_NARRATION.matcher(description).find()) {
                return Transaction.CREDIT;
            }
            if (DEBIT_NARRATION.matcher(description).find()) {
                return Transaction.DEBIT;
            }
        }
        // Statements with only positive amounts (e.g. card statements) list spending.
        return Transaction.DEBIT;
    }

    private static String invalidOrMissing(RawRow row, ColumnMapping mapping) {
        boolean anyText = (row.cell(mapping.debit()) != null && !row.cell(mapping.debit()).isBlank())
                || (row.cell(mapping.credit()) != null && !row.cell(mapping.credit()).isBlank());
        return anyText ? "INVALID_AMOUNT" : "MISSING_AMOUNT";
    }

    /** Uses the mode column when present, otherwise infers from description keywords. */
    static String paymentMethod(String mode, String description) {
        String text = ((mode == null ? "" : mode) + " " + description).toLowerCase(Locale.ROOT);
        if (text.contains("upi")) {
            return "upi";
        }
        if (text.contains("neft") || text.contains("imps") || text.contains("rtgs") || text.contains("net banking")
                || text.contains("netbanking")) {
            return "net_banking";
        }
        if (text.contains("atm") || text.contains("cash")) {
            return "cash";
        }
        if (text.contains("wallet") || text.contains("paytm")) {
            return "wallet";
        }
        if (text.contains("pos") || text.contains("card") || text.contains("ecom")) {
            return "card";
        }
        return null;
    }

    private static String truncate(String value) {
        return truncate(value, 1000);
    }

    private static String truncate(String value, int max) {
        return value == null || value.length() <= max ? value : value.substring(0, max);
    }

    private static final class RowRejected extends RuntimeException {
        private final String code;

        RowRejected(String code, String message) {
            super(message, null, false, false);
            this.code = code;
        }
    }
}

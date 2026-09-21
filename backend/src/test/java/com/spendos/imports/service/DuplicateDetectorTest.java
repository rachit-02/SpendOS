package com.spendos.imports.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.spendos.imports.validator.StatementRowValidator.Candidate;
import com.spendos.transactions.domain.Transaction;
import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class DuplicateDetectorTest {

    private static Candidate row(int n, String date, String amount, String description, String reference) {
        return new Candidate(n, "raw", LocalDate.parse(date), new BigDecimal(amount), "debit", description, reference, null);
    }

    private static Transaction existing(String date, String amount, String description, String reference) {
        Transaction transaction = new Transaction();
        transaction.setTransactionDate(LocalDate.parse(date));
        transaction.setAmount(new BigDecimal(amount));
        transaction.setTransactionType("debit");
        transaction.setRawDescription(description);
        transaction.setExternalReference(reference);
        try {
            Field id = transaction.getClass().getSuperclass().getDeclaredField("id");
            id.setAccessible(true);
            id.set(transaction, UUID.randomUUID());
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException(exception);
        }
        return transaction;
    }

    @Test
    void sameMerchantAndAmountWithinOneDayIsDuplicate() {
        var result = DuplicateDetector.detect(
                List.of(row(1, "2026-09-06", "450.00", "UPI-ZOMATO-99887766@okaxis", null)),
                List.of(existing("2026-09-05", "450", "ZOMATO", null)));

        assertThat(result.duplicates()).hasSize(1);
        assertThat(result.unique()).isEmpty();
    }

    @Test
    void twoDaysApartOrDifferentAmountIsNotDuplicate() {
        var result = DuplicateDetector.detect(
                List.of(row(1, "2026-09-07", "450", "ZOMATO", null), row(2, "2026-09-05", "451", "ZOMATO", null)),
                List.of(existing("2026-09-05", "450", "ZOMATO", null)));

        assertThat(result.duplicates()).isEmpty();
        assertThat(result.unique()).hasSize(2);
    }

    @Test
    void matchingBankReferenceIsExactDuplicateEvenAcrossDates() {
        var result = DuplicateDetector.detect(
                List.of(row(1, "2026-09-20", "999", "Something else", "UTR123")),
                List.of(existing("2026-09-01", "999", "Other text", "UTR123")));

        assertThat(result.duplicates()).singleElement().extracting(DuplicateDetector.Match::exact).isEqualTo(true);
    }

    @Test
    void eachExistingTransactionAbsorbsOnlyOneRow() {
        var result = DuplicateDetector.detect(
                List.of(row(1, "2026-09-05", "120", "Starbucks", null), row(2, "2026-09-05", "120", "Starbucks", null)),
                List.of(existing("2026-09-05", "120", "Starbucks", null)));

        assertThat(result.duplicates()).hasSize(1);
        assertThat(result.unique()).hasSize(1);
    }
}

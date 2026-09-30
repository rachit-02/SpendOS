package com.spendos.imports.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.spendos.imports.validator.StatementRowValidator.Candidate;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

class SelfTransferDetectorTest {

    private static final LocalDate DAY = LocalDate.of(2026, 8, 11);
    private static final String HOLDER = "Rachit Shrivastava";

    private static Candidate row(String type, String amount, String description) {
        return row(type, amount, description, DAY);
    }

    private static Candidate row(String type, String amount, String description, LocalDate date) {
        return new Candidate(1, description, date, new BigDecimal(amount), type, description, null, "upi");
    }

    private static List<String> types(List<Candidate> marked) {
        return marked.stream().map(Candidate::type).toList();
    }

    @Test
    void moneyFromAndToTheAccountHolderIsATransfer() {
        List<Candidate> marked = SelfTransferDetector.mark(List.of(
                row("credit", "2500", "Received from Mr RACHIT  SHRIVASTAVA"),
                row("debit", "108", "Paid to Mr RACHIT SHRIVASTAVA"),
                row("debit", "649", "Paid to NETFLIX.COM")), HOLDER);

        assertThat(types(marked)).containsExactly("transfer", "transfer", "debit");
    }

    @Test
    void narrationsThatSaySoAreTransfersWithoutAName() {
        List<Candidate> marked = SelfTransferDetector.mark(List.of(
                row("debit", "5000", "UPI/SELF TRANSFER/own account"),
                row("debit", "300", "UPI/RAJU TEA STALL")), null);

        assertThat(types(marked)).containsExactly("transfer", "debit");
    }

    @Test
    void theOtherLegIsPairedOnlyWhenItNamesAnAccount() {
        // The wallet was topped up from the holder's own bank and the money went straight out to an
        // account number: both legs are one transfer.
        List<Candidate> paired = SelfTransferDetector.mark(List.of(
                row("credit", "70000", "Received from Mr RACHIT SHRIVASTAVA"),
                row("debit", "70000", "Paid to 2657")), HOLDER);
        assertThat(types(paired)).containsExactly("transfer", "transfer");

        // Same amount on the same day, but a named payee is spending, not the other leg of a transfer.
        List<Candidate> spending = SelfTransferDetector.mark(List.of(
                row("credit", "1000", "Received from Mr RACHIT SHRIVASTAVA"),
                row("debit", "1000", "Paid to BLINKIT COMMERCE PRIVATE LIMITED")), HOLDER);
        assertThat(types(spending)).containsExactly("transfer", "debit");
    }

    @Test
    void somebodyElseIsNotTheAccountHolder() {
        List<Candidate> marked = SelfTransferDetector.mark(List.of(
                row("credit", "1500", "Received from Jyoti Shrivastava"),
                row("credit", "11727", "Received from Nimit"),
                row("debit", "160", "Paid to Kajal Sen")), HOLDER);

        assertThat(types(marked)).containsExactly("credit", "credit", "debit");
    }

    @Test
    void aSingleWordNameOnlyMatchesTheWholeName() {
        // "Received from Nimit Sharma" is not Nimit himself: too common a word to match loosely.
        List<Candidate> marked = SelfTransferDetector.mark(List.of(
                row("credit", "500", "Received from Nimit Sharma"),
                row("credit", "600", "Received from Nimit")), "Nimit");

        assertThat(types(marked)).containsExactly("credit", "transfer");
    }

    @Test
    void legsMoreThanADayApartAreNotPaired() {
        List<Candidate> marked = SelfTransferDetector.mark(List.of(
                row("credit", "70000", "Received from Mr RACHIT SHRIVASTAVA", DAY),
                row("debit", "70000", "Paid to 2657", DAY.plusDays(3))), HOLDER);

        assertThat(types(marked)).containsExactly("transfer", "debit");
    }

    @Test
    void withoutANameOnlyExplicitSelfTransfersAreMarked() {
        List<Candidate> marked = SelfTransferDetector.mark(List.of(
                row("credit", "2500", "Received from Mr RACHIT SHRIVASTAVA"),
                row("debit", "5000", "NEFT self transfer")), "   ");

        assertThat(types(marked)).containsExactly("credit", "transfer");
    }
}

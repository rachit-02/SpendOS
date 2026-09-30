package com.spendos.imports.validator;

import static org.assertj.core.api.Assertions.assertThat;

import com.spendos.imports.parser.CsvStatementParser;
import com.spendos.imports.parser.CsvStatementParser.ParsedFile;
import com.spendos.imports.validator.StatementRowValidator.Candidate;
import com.spendos.imports.validator.StatementRowValidator.Result;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class StatementRowValidatorTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 9, 21);

    private static Result validate(String csv) {
        ParsedFile file = CsvStatementParser.parse(csv.getBytes(StandardCharsets.UTF_8));
        return StatementRowValidator.validate(file.rows(), file.mapping(), null, TODAY);
    }

    @Test
    void signedAmountsGiveDebitsAndCredits() {
        Result result = validate("Date,Description,Amount\n05-09-2026,UPI-ZOMATO,-450\n01-09-2026,SALARY ACME,75000\n");

        assertThat(result.errors()).isEmpty();
        Candidate zomato = result.candidates().get(0);
        assertThat(zomato.type()).isEqualTo("debit");
        assertThat(zomato.amount()).isEqualByComparingTo("450");
        assertThat(zomato.date()).isEqualTo(LocalDate.of(2026, 9, 5));
        assertThat(zomato.paymentMethod()).isEqualTo("upi");
        assertThat(result.candidates().get(1).type()).isEqualTo("credit");
    }

    @Test
    void allPositiveAmountsWithoutTypeColumnAreSpending() {
        Result result = validate("Date,Description,Amount\n05-09-2026,Netflix,499\n06-09-2026,Uber,250\n");

        assertThat(result.candidates()).extracting(Candidate::type).containsOnly("debit");
    }

    @Test
    void walletNarrationDecidesTheDirectionWhenAmountsAreUnsigned() {
        // Wallet statements (Google Pay, PhonePe) have one unsigned amount column; a credit row says
        // "Received from <someone>" and then names the account the money was paid to, so credit wins.
        Result result = validate("""
                Date & time,Transaction details,Amount
                05-09-2026,Paid to BLINKIT COMMERCE PRIVATE LIMITED,177
                06-09-2026,Received from Ananya Rao Paid to Bank of Baroda 9384,150
                07-09-2026,Sent to Kajal Sen,160
                08-09-2026,SOME OTHER NARRATION,99
                """);

        assertThat(result.errors()).isEmpty();
        assertThat(result.candidates()).extracting(Candidate::type)
                .containsExactly("debit", "credit", "debit", "debit");
    }

    @Test
    void typeColumnAndDebitCreditColumnsAreRespected() {
        Result typed = validate("Date,Description,Amount,Type\n05-09-2026,Refund,100,CR\n06-09-2026,Uber,250,DR\n");
        assertThat(typed.candidates()).extracting(Candidate::type).containsExactly("credit", "debit");

        Result split = validate("Date,Narration,Debit,Credit\n05-09-2026,ATM,2000,\n06-09-2026,Interest,,12.50\n");
        assertThat(split.candidates()).extracting(Candidate::type).containsExactly("debit", "credit");
        assertThat(split.candidates().get(1).amount()).isEqualByComparingTo("12.50");
    }

    @Test
    void invalidRowsAreCollectedWithCodesWithoutStoppingOthers() {
        Result result = validate("Date,Description,Amount\n"
                + "05-09-2026,Good row,100\n"
                + ",Missing date,100\n"
                + "31-02-2026,Bad date,100\n"
                + "05-09-2026,,100\n"
                + "05-09-2026,Bad amount,abc\n"
                + "05-09-2026,Zero,0\n"
                + "05-09-2027,Future,100\n"
                + "05-09-2026,Missing amount,\n");

        assertThat(result.candidates()).hasSize(1);
        assertThat(result.errors()).extracting(StatementRowValidator.RowError::code).containsExactly(
                "MISSING_DATE", "INVALID_DATE", "MISSING_DESCRIPTION", "INVALID_AMOUNT", "INVALID_AMOUNT",
                "INVALID_DATE", "MISSING_AMOUNT");
        assertThat(result.errors().get(0).rowNumber()).isEqualTo(3);
        assertThat(result.errors().get(0).rawLine()).isEqualTo(",Missing date,100");
    }

    @Test
    void usMonthFirstFileIsDetected() {
        Result result = validate("Date,Description,Amount\n09/25/2026,A,1\n09/05/2026,B,2\n");
        // 25 cannot be a month, so the file is month-first; the second row is 5 September not 9 May
        assertThat(result.errors()).singleElement().extracting(StatementRowValidator.RowError::code)
                .isEqualTo("INVALID_DATE"); // 25 Sept 2026 is after "today" (21 Sept)
        assertThat(result.candidates().get(0).date()).isEqualTo(LocalDate.of(2026, 9, 5));
    }
}

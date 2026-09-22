package com.spendos.imports.parser;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.spendos.common.exception.ApiException;
import com.spendos.imports.parser.CsvStatementParser.ParsedFile;
import com.spendos.imports.parser.CsvStatementParser.RawRow;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Parses real PDF files in src/test/resources/statements: synthetic statements (fake data) rendered by
 * Chrome's print-to-PDF and by OpenPDF in several bank layouts, plus broken and scanned PDFs. See
 * tools/pdf-fixtures for how they are generated.
 */
class PdfStatementParserTest {

    private static byte[] fixture(String name) throws IOException {
        return Files.readAllBytes(Path.of("src/test/resources/statements", name));
    }

    private static ParsedFile parse(String name) throws IOException {
        return PdfStatementParser.parse(fixture(name));
    }

    private static String cell(RawRow row, int column) {
        return row.cell(column);
    }

    private static RawRow rowContaining(ParsedFile file, int column, String text) {
        return file.rows().stream().filter(r -> cell(r, column).contains(text)).findFirst().orElseThrow();
    }

    @Test
    void recognisesPdfsByContentNotName() throws IOException {
        assertThat(PdfStatementParser.isPdf(fixture("hdfc-style.pdf"))).isTrue();
        assertThat(PdfStatementParser.isPdf("Date,Description,Amount\n".getBytes(StandardCharsets.UTF_8))).isFalse();
        assertThat(PdfStatementParser.isPdf(new byte[] {'%', 'P'})).isFalse();
    }

    @Test
    void hdfcStyleWithSplitHeadersWrappedNarrationsAndTwoPages() throws IOException {
        ParsedFile file = parse("hdfc-style.pdf");
        ColumnMapping m = file.mapping();

        assertThat(file.format()).isEqualTo("pdf");
        assertThat(file.header()).containsExactly("Date", "Narration", "Chq./Ref.No.", "Value Dt", "Withdrawal Amt.",
                "Deposit Amt.", "Closing Balance");
        // 37 transactions across two pages; the account block, repeated header, summary table and footers are ignored.
        assertThat(file.rows()).hasSize(37);

        RawRow salary = file.rows().get(0);
        assertThat(cell(salary, m.date())).isEqualTo("01/08/26");
        assertThat(cell(salary, m.description())).isEqualTo("NEFT CR-ACME TECHNOLOGIES PVT LTD-SALARY FOR JULY 2026-ACMEPAY0012");
        assertThat(cell(salary, m.credit())).isEqualTo("85,000.00"); // the Deposit column, by position
        assertThat(cell(salary, m.debit())).isEmpty();
        assertThat(cell(salary, m.reference())).isEqualTo("NEFT0012345");

        RawRow rent = file.rows().get(1);
        assertThat(cell(rent, m.description()))
                .isEqualTo("UPI/422345678901/PRESTIGE RESIDENCY RENT/rent@okhdfcbank/Monthly rent August");
        assertThat(cell(rent, m.debit())).isEqualTo("22,000.00");

        RawRow lastOnPageTwo = file.rows().get(36);
        assertThat(cell(lastOnPageTwo, m.date())).isEqualTo("20/09/26");
        assertThat(cell(lastOnPageTwo, m.credit())).isEqualTo("1,210.00");
        assertThat(file.rows()).noneMatch(r -> String.join(" ", r.cells()).matches(".*(STATEMENT SUMMARY|Page \\d|Generated On).*"));
    }

    @Test
    void sbiStyleWithDatesWrappedOverThreeLines() throws IOException {
        ParsedFile file = parse("sbi-style.pdf");
        ColumnMapping m = file.mapping();

        assertThat(file.rows()).hasSize(37); // spans three pages
        assertThat(file.header().get(m.date())).isEqualTo("Txn Date");
        RawRow first = file.rows().get(0);
        assertThat(cell(first, m.date())).isEqualTo("1 Aug 2026"); // "1" / "Aug" / "2026" in the PDF
        assertThat(cell(first, m.description()))
                .isEqualTo("BY TRANSFER-NEFT CR-ACME TECHNOLOGIES PVT LTD-SALARY FOR JULY 2026-ACMEPAY0012");
        assertThat(cell(first, m.credit())).isEqualTo("85,000.00");
        RawRow laptop = rowContaining(file, m.description(), "CROMA");
        assertThat(cell(laptop, m.date())).isEqualTo("14 Sep 2026");
        assertThat(cell(laptop, m.debit())).isEqualTo("45,999.00");
    }

    @Test
    void iciciStyleKeepsNarrowNeighbouringColumnsApart() throws IOException {
        ParsedFile file = parse("icici-style.pdf");
        ColumnMapping m = file.mapping();

        assertThat(file.rows()).hasSize(37);
        assertThat(file.header().get(m.date())).isEqualTo("Transaction Date");
        assertThat(file.header().get(m.debit())).isEqualTo("Withdrawal Amount (INR )");
        // The cheque column's "-" must not leak into the date next to it.
        assertThat(file.rows()).allSatisfy(r -> assertThat(cell(r, m.date())).matches("\\d{2}/\\d{2}/2026"));
        RawRow zomato = rowContaining(file, m.description(), "ZOMATO");
        assertThat(cell(zomato, m.debit())).isEqualTo("486.00");
        assertThat(cell(zomato, m.credit())).isEqualTo("0.00");
    }

    @Test
    void creditCardStyleWithDrCrSuffixes() throws IOException {
        ParsedFile file = parse("credit-card-style.pdf");
        ColumnMapping m = file.mapping();

        assertThat(file.rows()).hasSize(10);
        assertThat(cell(file.rows().get(0), m.amount())).isEqualTo("18,450.00 Cr");
        assertThat(cell(file.rows().get(1), m.description())).isEqualTo("SWIGGY BANGALORE IN");
        assertThat(cell(file.rows().get(1), m.amount())).isEqualTo("412.00 Dr");
    }

    @Test
    void usStyleWithParenthesisedNegatives() throws IOException {
        ParsedFile file = parse("us-bank-style.pdf");
        ColumnMapping m = file.mapping();

        assertThat(file.rows()).hasSize(7);
        assertThat(file.header().get(m.date())).isEqualTo("Posting Date");
        assertThat(cell(file.rows().get(1), m.amount())).isEqualTo("(6.45)");
        assertThat(cell(file.rows().get(1), m.description())).isEqualTo("CHECKCARD 0803 STARBUCKS STORE 1234 SEATTLE WA");
    }

    @Test
    void verticallyCentredRowsKeepWrappedTextWithTheRightTransaction() throws IOException {
        ParsedFile centred = parse("vertically-centred-rows.pdf");
        ParsedFile topAligned = parse("hdfc-style.pdf");
        ColumnMapping m = centred.mapping();

        assertThat(centred.rows()).hasSize(37);
        List<String> expected = topAligned.rows().stream().map(r -> cell(r, m.description())).toList();
        assertThat(centred.rows().stream().map(r -> cell(r, m.description())).toList()).isEqualTo(expected);
    }

    private static void assertRejected(String name, String code, String messagePart) {
        assertThatThrownBy(() -> parse(name))
                .isInstanceOfSatisfying(ApiException.class, e -> {
                    assertThat(e.getCode()).isEqualTo(code);
                    assertThat(e.getStatus().value()).isEqualTo(400);
                })
                .hasMessageContaining(messagePart);
    }

    @Test
    void scannedPdfsAreRejectedWithAnExplanationNotAnEmptyImport() {
        assertRejected("scanned-image-only.pdf", "PDF_SCANNED_IMAGE", "needs OCR");
        // A mixed file must not be imported partially.
        assertRejected("partly-scanned.pdf", "PDF_PARTLY_SCANNED", "Pages [2]");
    }

    @Test
    void brokenPasswordProtectedAndNonStatementPdfsGetClearErrors() {
        assertRejected("password-protected.pdf", "PDF_PASSWORD_PROTECTED", "password-protected");
        assertRejected("truncated.pdf", "PDF_UNREADABLE", "damaged or incomplete");
        assertRejected("no-transaction-table.pdf", "PDF_NO_TABLE", "Couldn't find a transaction table");
        assertThatThrownBy(() -> PdfStatementParser.parse("%PDF-1.7\nnot really a pdf".getBytes(StandardCharsets.US_ASCII)))
                .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.getCode()).isEqualTo("PDF_UNREADABLE"));
    }
}

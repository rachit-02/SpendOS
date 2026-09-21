package com.spendos.imports.parser;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.spendos.common.exception.ApiException;
import com.spendos.imports.parser.CsvStatementParser.ParsedFile;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class CsvStatementParserTest {

    private static ParsedFile parse(String content) {
        return CsvStatementParser.parse(content.getBytes(StandardCharsets.UTF_8));
    }

    @Test
    void parsesSimpleCommaFileWithHeader() {
        ParsedFile file = parse("Date,Description,Amount\n05-09-2026,ZOMATO ONLINE,-450.00\n06-09-2026,Salary,75000\n");

        assertThat(file.delimiter()).isEqualTo(',');
        assertThat(file.hasHeader()).isTrue();
        assertThat(file.mapping().date()).isZero();
        assertThat(file.mapping().description()).isEqualTo(1);
        assertThat(file.mapping().amount()).isEqualTo(2);
        assertThat(file.rows()).hasSize(2);
        assertThat(file.rows().get(0).cell(1)).isEqualTo("ZOMATO ONLINE");
        assertThat(file.rows().get(0).rowNumber()).isEqualTo(2);
    }

    @Test
    void detectsSemicolonDelimiterAndQuotedFields() {
        ParsedFile file = parse("Txn Date;Narration;Withdrawal Amt.;Deposit Amt.\n"
                + "05/09/2026;\"UPI; ZOMATO\";450,00;\n06/09/2026;SALARY;;75000,00\n");

        assertThat(file.delimiter()).isEqualTo(';');
        assertThat(file.mapping().debit()).isEqualTo(2);
        assertThat(file.mapping().credit()).isEqualTo(3);
        assertThat(file.rows().get(0).cell(1)).isEqualTo("UPI; ZOMATO");
    }

    @Test
    void skipsBankPreambleBeforeTheHeader() {
        ParsedFile file = parse("Account Statement\nAccount No: XXXX1234\n\n"
                + "Value Date,Transaction Date,Particulars,Chq/Ref No,Debit,Credit,Balance\n"
                + "05-09-2026,05-09-2026,UPI-ZOMATO-123,REF1,450.00,,10000\n");

        assertThat(file.mapping().date()).isEqualTo(1);
        assertThat(file.mapping().description()).isEqualTo(2);
        assertThat(file.mapping().reference()).isEqualTo(3);
        assertThat(file.rows()).hasSize(1);
    }

    @Test
    void infersColumnsForHeaderlessTabFile() {
        ParsedFile file = parse("2026-09-05\tNetflix subscription\t499\n2026-09-06\tUber ride to office\t250\n");

        assertThat(file.delimiter()).isEqualTo('\t');
        assertThat(file.hasHeader()).isFalse();
        assertThat(file.mapping().date()).isZero();
        assertThat(file.mapping().description()).isEqualTo(1);
        assertThat(file.mapping().amount()).isEqualTo(2);
        assertThat(file.rows()).hasSize(2);
    }

    @Test
    void decodesWindows1252AndStripsUtf8Bom() {
        byte[] latin = "Date,Description,Amount\n05-09-2026,Café Coffee Day,120\n".getBytes(Charset.forName("windows-1252"));
        assertThat(CsvStatementParser.parse(latin).rows().get(0).cell(1)).isEqualTo("Café Coffee Day");

        ParsedFile bom = parse("\uFEFFDate,Description,Amount\n05-09-2026,Tea,20\n");
        assertThat(bom.mapping().date()).isZero();
    }

    @Test
    void rejectsFilesWithoutRecognizableColumns() {
        assertThatThrownBy(() -> parse("hello world\nthis is not a statement\n"))
                .isInstanceOf(ApiException.class)
                .extracting("code").isEqualTo("INVALID_FILE_TYPE");
        assertThatThrownBy(() -> parse("   \n  \n"))
                .isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> parse("Date,Description,Amount\n"))
                .hasMessageContaining("no transaction rows");
    }
}

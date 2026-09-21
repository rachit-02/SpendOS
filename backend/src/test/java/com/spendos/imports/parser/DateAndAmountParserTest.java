package com.spendos.imports.parser;

import static org.assertj.core.api.Assertions.assertThat;

import com.spendos.imports.parser.DateParser.Order;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.api.Test;

class DateAndAmountParserTest {

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "2026-09-05|DMY|2026-09-05",
            "05-09-2026|DMY|2026-09-05",
            "05/09/2026|DMY|2026-09-05",
            "09/05/2026|MDY|2026-09-05",
            "05.09.2026|DMY|2026-09-05",
            "05/09/26|DMY|2026-09-05",
            "05-Sep-2026|DMY|2026-09-05",
            "05 Sep 2026|DMY|2026-09-05",
            "5 September 2026|DMY|2026-09-05",
            "Sep 5, 2026|DMY|2026-09-05",
            "20260905|DMY|2026-09-05",
            "2026/09/05|MDY|2026-09-05",
            "05/09/2026 14:22:10|DMY|2026-09-05"})
    void parsesCommonStatementDateFormats(String text, Order order, LocalDate expected) {
        assertThat(DateParser.parse(text, order)).isEqualTo(expected);
    }

    @ParameterizedTest
    @CsvSource({"31/02/2026", "hello", "13/13/2026", "''"})
    void rejectsInvalidDates(String text) {
        assertThat(DateParser.parse(text, Order.DMY)).isNull();
    }

    @Test
    void detectsDayMonthOrderFromTheFile() {
        assertThat(DateParser.detectOrder(List.of("05/09/2026", "25/09/2026"), null)).isEqualTo(Order.DMY);
        assertThat(DateParser.detectOrder(List.of("09/05/2026", "09/25/2026"), null)).isEqualTo(Order.MDY);
        assertThat(DateParser.detectOrder(List.of("05/09/2026"), Order.MDY)).isEqualTo(Order.MDY);
        assertThat(DateParser.detectOrder(List.of("05/09/2026"), null)).isEqualTo(Order.DMY);
    }

    @Test
    void hintNamesMapToOrders() {
        assertThat(DateParser.orderFromHint("DD-MM-YYYY")).isEqualTo(Order.DMY);
        assertThat(DateParser.orderFromHint("MM/DD/YYYY")).isEqualTo(Order.MDY);
        assertThat(DateParser.orderFromHint("YYYY-MM-DD")).isNull();
        assertThat(DateParser.orderFromHint(null)).isNull();
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "450|450|",
            "1,234.50|1234.50|",
            "₹ 1,234.50|1234.50|",
            "Rs. 450.00|450.00|",
            "INR 12,00,000.00|1200000.00|",
            "-450.00|-450.00|",
            "(450.00)|-450.00|",
            "450.00-|-450.00|",
            "450.00 Dr|450.00|debit",
            "450.00 CR|450.00|credit",
            "1.234,50|1234.50|",
            "+99.9|99.9|"})
    void parsesAmountFormats(String text, BigDecimal expected, String marker) {
        AmountParser.ParsedAmount parsed = AmountParser.parse(text);
        assertThat(parsed).isNotNull();
        assertThat(parsed.value()).isEqualByComparingTo(expected);
        assertThat(parsed.marker()).isEqualTo(marker);
    }

    @ParameterizedTest
    @CsvSource({"abc", "12a", "''", "-", "1.2.3"})
    void rejectsNonNumericAmounts(String text) {
        assertThat(AmountParser.parse(text)).isNull();
    }
}

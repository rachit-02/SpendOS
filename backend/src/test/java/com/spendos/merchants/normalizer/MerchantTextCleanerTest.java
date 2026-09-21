package com.spendos.merchants.normalizer;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class MerchantTextCleanerTest {

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "UPI-ZOMATO ONLINE-9876543210@okaxis-REF12345678|Zomato Online",
            "POS 4521XXXXXXXX1234 STARBUCKS COFFEE|Starbucks Coffee",
            "NEFT/AXIS0001234/ACME TECHNOLOGIES PVT LTD|Acme Technologies",
            "ATM WDL 1234567 MG ROAD|ATM Wdl Mg Road",
            "   |Unknown",
            "123456789|Unknown",
            "IRCTC E-TICKET|IRCTC E Ticket"})
    void cleansRawStatementText(String raw, String expected) {
        assertThat(MerchantTextCleaner.clean(raw)).isEqualTo(expected);
    }
}

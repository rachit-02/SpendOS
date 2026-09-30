package com.spendos.imports;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.spendos.support.IntegrationTestBase;
import com.spendos.support.TestAuth;
import com.spendos.support.TestAuth.Session;
import com.spendos.support.TestData;
import com.spendos.transactions.domain.Account;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/**
 * PDF statements through the same endpoint and pipeline as CSV: upload, parse, validate, dedupe,
 * merchant normalization and categorization, using the real fixture PDFs.
 */
class PdfImportIntegrationTest extends IntegrationTestBase {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private TestData testData;

    @Autowired
    private JdbcTemplate jdbc;

    private Session session;
    private Account account;

    @BeforeEach
    void setUp() throws Exception {
        session = TestAuth.newSession(mockMvc);
        account = testData.account(session.userId());
    }

    private static byte[] fixture(String name) throws Exception {
        return Files.readAllBytes(Path.of("src/test/resources/statements", name));
    }

    private ResultActions upload(String name, String contentType, byte[] content) throws Exception {
        return mockMvc.perform(multipart("/v1/imports/upload")
                .file(new MockMultipartFile("file", name, contentType, content))
                .param("accountId", account.getId().toString())
                .header("Authorization", session.bearer()));
    }

    private JsonNode importAndWait(String name, String contentType, byte[] content) throws Exception {
        String body = upload(name, contentType, content).andExpect(status().isAccepted())
                .andReturn().getResponse().getContentAsString();
        String jobId = TestAuth.read(body).get("data").get("importJobId").asText();
        long deadline = System.currentTimeMillis() + 30_000;
        while (System.currentTimeMillis() < deadline) {
            JsonNode job = TestAuth.read(mockMvc.perform(get("/v1/imports/" + jobId).header("Authorization", session.bearer()))
                    .andReturn().getResponse().getContentAsString()).get("data");
            if (job.get("importStatus").asText().matches("completed|failed")) {
                return job;
            }
            Thread.sleep(100);
        }
        throw new AssertionError("Import did not finish in time");
    }

    /** Category name of each imported transaction, keyed by its canonical merchant (first seen). */
    private Map<String, String> categoriesByMerchant() {
        Map<String, String> result = new HashMap<>();
        jdbc.query("""
                SELECT m.merchant_name, c.category_name FROM transactions t
                JOIN merchants m ON m.id = t.merchant_id JOIN categories c ON c.id = t.category_id
                WHERE t.user_id = ?""", rs -> {
            result.putIfAbsent(rs.getString(1), rs.getString(2));
        }, session.userId());
        return result;
    }

    @Test
    void textPdfStatementIsImportedAndCategorisedLikeCsv() throws Exception {
        JsonNode job = importAndWait("hdfc-style.pdf", "application/pdf", fixture("hdfc-style.pdf"));

        assertThat(job.get("importStatus").asText()).isEqualTo("completed");
        assertThat(job.get("importedCount").asInt()).isEqualTo(37);
        assertThat(job.get("invalidCount").asInt()).isZero();
        assertThat(job.get("duplicateCount").asInt()).isZero();
        assertThat(job.get("errorSummary").asText()).contains("\"format\":\"pdf\"").contains("\"dateOrder\":\"DMY\"");

        // Totals match the statement's own summary block: debits 1,21,215.50 and credits 1,71,210.00.
        Map<String, Object> totals = jdbc.queryForMap("""
                SELECT SUM(amount) FILTER (WHERE transaction_type = 'debit') AS debits,
                       SUM(amount) FILTER (WHERE transaction_type = 'credit') AS credits
                FROM transactions WHERE user_id = ?""", session.userId());
        assertThat((BigDecimal) totals.get("debits")).isEqualByComparingTo("121215.50");
        assertThat((BigDecimal) totals.get("credits")).isEqualByComparingTo("171210.00");

        // The shared merchant normalizer and category engine did the categorising.
        Map<String, String> categories = categoriesByMerchant();
        assertThat(categories).containsEntry("Zomato", "Food").containsEntry("Swiggy", "Food")
                .containsEntry("Netflix", "Subscriptions").containsEntry("Uber", "Transport")
                .containsEntry("Indian Oil", "Transport").containsEntry("BESCOM", "Bills")
                .containsEntry("Croma", "Shopping");
        String salaryCategory = jdbc.queryForObject("""
                SELECT c.category_name FROM transactions t JOIN categories c ON c.id = t.category_id
                WHERE t.user_id = ? AND t.transaction_date = '2026-08-01'""", String.class, session.userId());
        assertThat(salaryCategory).isEqualTo("Income");
    }

    @Test
    void otherLayoutsImportThroughTheSameEndpoint() throws Exception {
        JsonNode sbi = importAndWait("sbi.pdf", "application/pdf", fixture("sbi-style.pdf"));
        assertThat(sbi.get("importedCount").asInt()).isEqualTo(37);

        // A separate user: duplicate detection is per user across accounts, and the card statement shares a
        // Netflix charge (649 on 5 Sep) with the SBI one, which it would rightly flag as a possible duplicate.
        session = TestAuth.newSession(mockMvc);
        account = testData.account(session.userId());
        JsonNode card = importAndWait("card.pdf", "application/pdf", fixture("credit-card-style.pdf"));
        assertThat(card.get("importedCount").asInt()).as(card.toString()).isEqualTo(10);
        Map<String, Object> payment = jdbc.queryForMap("""
                SELECT transaction_type, amount FROM transactions
                WHERE user_id = ? AND raw_description LIKE 'PAYMENT RECEIVED%'""", session.userId());
        assertThat(payment.get("transaction_type")).isEqualTo("credit"); // "18,450.00 Cr"

        JsonNode us = importAndWait("us.pdf", "application/pdf", fixture("us-bank-style.pdf"));
        assertThat(us.get("importedCount").asInt()).isEqualTo(7);
        assertThat(us.get("errorSummary").asText()).contains("\"dateOrder\":\"MDY\"");
        Map<String, Object> starbucks = jdbc.queryForMap("""
                SELECT transaction_type, amount, transaction_date FROM transactions
                WHERE user_id = ? AND raw_description LIKE '%STARBUCKS%'""", session.userId());
        assertThat(starbucks.get("transaction_type")).isEqualTo("debit"); // "(6.45)"
        assertThat((BigDecimal) starbucks.get("amount")).isEqualByComparingTo("6.45");
        assertThat(starbucks.get("transaction_date").toString()).isEqualTo("2026-08-03");
    }

    /**
     * A wallet statement: one unsigned amount column, with the direction of the money only in the
     * narration. Every row used to be rejected as "Unrecognized date '01 Au'" because the widely spaced
     * header labels each became a column.
     */
    @Test
    void walletStatementImportsWithTheDirectionReadFromTheNarration() throws Exception {
        session = TestAuth.newSession(mockMvc);
        account = testData.account(session.userId());

        JsonNode wallet = importAndWait("wallet.pdf", "application/pdf", fixture("wallet-style.pdf"));
        assertThat(wallet.get("invalidCount").asInt()).as(wallet.toString()).isZero();
        assertThat(wallet.get("importedCount").asInt()).isEqualTo(26);

        Map<String, Object> counts = jdbc.queryForMap("""
                SELECT count(*) FILTER (WHERE transaction_type = 'credit') AS credits,
                       count(*) FILTER (WHERE transaction_type = 'debit') AS debits
                FROM transactions WHERE user_id = ?""", session.userId());
        // "Received from ..." is money in, "Paid to ..." money out; without the narration every row
        // would have been spending.
        assertThat(((Number) counts.get("credits")).intValue()).isEqualTo(2);
        assertThat(((Number) counts.get("debits")).intValue()).isEqualTo(24);

        Map<String, Object> received = jdbc.queryForMap("""
                SELECT transaction_type, amount, transaction_date FROM transactions
                WHERE user_id = ? AND raw_description LIKE 'Received from%' ORDER BY transaction_date LIMIT 1""",
                session.userId());
        assertThat(received.get("transaction_type")).isEqualTo("credit");
        assertThat((BigDecimal) received.get("amount")).isEqualByComparingTo("85000");
        assertThat(received.get("transaction_date").toString()).isEqualTo("2026-08-01");
    }

    @Test
    void sameFileTwiceIsRejectedAndAPdfWithACsvNameStillWorks() throws Exception {
        byte[] pdf = fixture("icici-style.pdf");
        importAndWait("statement.csv", "text/csv", pdf); // named .csv, detected as PDF by content
        upload("statement.pdf", "application/pdf", pdf)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("DUPLICATE_IMPORT"));
    }

    @Test
    void badPdfsFailWithClearMessagesAndCreateNoImport() throws Exception {
        String[][] cases = {
            {"scanned-image-only.pdf", "PDF_SCANNED_IMAGE", "needs OCR"},
            {"partly-scanned.pdf", "PDF_PARTLY_SCANNED", "incomplete"},
            {"password-protected.pdf", "PDF_PASSWORD_PROTECTED", "password-protected"},
            {"truncated.pdf", "PDF_UNREADABLE", "damaged or incomplete"},
            {"no-transaction-table.pdf", "PDF_NO_TABLE", "Couldn't find a transaction table"},
        };
        for (String[] c : cases) {
            upload(c[0], "application/pdf", fixture(c[0]))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error.code").value(c[1]))
                    .andExpect(jsonPath("$.error.message").value(org.hamcrest.Matchers.containsString(c[2])));
        }
        upload("statement.pdf", "application/pdf", "Date,Description,Amount\n".getBytes(StandardCharsets.UTF_8))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_PDF"));

        Integer jobs = jdbc.queryForObject("SELECT COUNT(*) FROM import_jobs WHERE user_id = ?", Integer.class,
                session.userId());
        Integer transactions = jdbc.queryForObject("SELECT COUNT(*) FROM transactions WHERE user_id = ?", Integer.class,
                session.userId());
        assertThat(jobs).isZero();
        assertThat(transactions).isZero();
    }
}

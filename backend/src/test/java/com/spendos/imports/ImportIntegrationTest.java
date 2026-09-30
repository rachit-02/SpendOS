package com.spendos.imports;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
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
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

class ImportIntegrationTest extends IntegrationTestBase {

    private static final String STATEMENT = """
            Date,Description,Amount,Reference
            01-09-2026,SALARY ACME TECHNOLOGIES,75000.00,SAL001
            03-09-2026,UPI-ZOMATO ONLINE-98765@okaxis,-450.00,UPI001
            04-09-2026,UPI-SWIGGY-12345@ybl,-380.50,UPI002
            05-09-2026,NETFLIX.COM,-499.00,CARD01
            06-09-2026,POS UBER INDIA,-250.00,POS001
            07-09-2026,Local Kirana Store,-640.00,UPI003
            ,Missing date row,-100.00,BAD1
            08-09-2026,Bad amount row,abc,BAD2
            """;

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

    private ResultActions upload(Session who, String name, String contentType, byte[] content, UUID accountId)
            throws Exception {
        var request = multipart("/v1/imports/upload")
                .file(new MockMultipartFile("file", name, contentType, content))
                .header("Authorization", who.bearer());
        if (accountId != null) {
            request.param("accountId", accountId.toString());
        }
        return mockMvc.perform(request);
    }

    private String uploadAndWait(String csv) throws Exception {
        String body = upload(session, "sept.csv", "text/csv", csv.getBytes(StandardCharsets.UTF_8), account.getId())
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.data.status").value("processing"))
                .andReturn().getResponse().getContentAsString();
        String jobId = TestAuth.read(body).get("data").get("importJobId").asText();
        waitForCompletion(jobId);
        return jobId;
    }

    private JsonNode waitForCompletion(String jobId) throws Exception {
        long deadline = System.currentTimeMillis() + 30_000;
        while (System.currentTimeMillis() < deadline) {
            String body = mockMvc.perform(get("/v1/imports/" + jobId + "/status").header("Authorization", session.bearer()))
                    .andReturn().getResponse().getContentAsString();
            JsonNode data = TestAuth.read(body).get("data");
            String status = data.get("status").asText();
            if ("completed".equals(status) || "failed".equals(status)) {
                return data;
            }
            Thread.sleep(100);
        }
        throw new AssertionError("Import did not finish in time");
    }

    @Test
    void importsValidRowsReportsInvalidOnesAndCategorizesMerchants() throws Exception {
        String jobId = uploadAndWait(STATEMENT);

        mockMvc.perform(get("/v1/imports/" + jobId + "/status").header("Authorization", session.bearer()))
                .andExpect(jsonPath("$.data.status").value("completed"))
                .andExpect(jsonPath("$.data.progress.percentage").value(100))
                .andExpect(jsonPath("$.data.progress.total").value(8));

        mockMvc.perform(get("/v1/imports/" + jobId).header("Authorization", session.bearer()))
                .andExpect(jsonPath("$.data.importStatus").value("completed"))
                .andExpect(jsonPath("$.data.importedCount").value(6))
                .andExpect(jsonPath("$.data.invalidCount").value(2))
                .andExpect(jsonPath("$.data.duplicateCount").value(0))
                .andExpect(jsonPath("$.data.totalRowsProcessed").value(8))
                .andExpect(jsonPath("$.data.fileName").value("sept.csv"));

        mockMvc.perform(get("/v1/imports/" + jobId + "/errors").header("Authorization", session.bearer()))
                .andExpect(jsonPath("$.data", hasSize(2)))
                .andExpect(jsonPath("$.data[0].rowNumber").value(8))
                .andExpect(jsonPath("$.data[0].errorCode").value("MISSING_DATE"))
                .andExpect(jsonPath("$.data[0].rawData").value(",Missing date row,-100.00,BAD1"))
                .andExpect(jsonPath("$.data[1].errorCode").value("INVALID_AMOUNT"));

        mockMvc.perform(get("/v1/transactions?pageSize=50&sortBy=date&sortOrder=asc").header("Authorization", session.bearer()))
                .andExpect(jsonPath("$.data", hasSize(6)))
                .andExpect(jsonPath("$.data[0].transactionType").value("credit"))
                .andExpect(jsonPath("$.data[0].categoryName").value("Income"))
                .andExpect(jsonPath("$.data[0].subcategoryName").value("Salary"))
                .andExpect(jsonPath("$.data[1].merchantName").value("Zomato"))
                .andExpect(jsonPath("$.data[1].categoryName").value("Food"))
                .andExpect(jsonPath("$.data[1].subcategoryName").value("Food Delivery"))
                .andExpect(jsonPath("$.data[1].paymentMethod").value("upi"))
                .andExpect(jsonPath("$.data[1].externalReference").value("UPI001"))
                .andExpect(jsonPath("$.data[2].merchantName").value("Swiggy"))
                .andExpect(jsonPath("$.data[3].merchantName").value("Netflix"))
                .andExpect(jsonPath("$.data[3].categoryName").value("Subscriptions"))
                .andExpect(jsonPath("$.data[4].merchantName").value("Uber"))
                .andExpect(jsonPath("$.data[4].categoryName").value("Transport"))
                .andExpect(jsonPath("$.data[5].categoryName").value("Food"))
                .andExpect(jsonPath("$.data[5].subcategoryName").value("Groceries"));

        mockMvc.perform(get("/v1/imports/history").header("Authorization", session.bearer()))
                .andExpect(jsonPath("$.data", hasSize(1)))
                .andExpect(jsonPath("$.data[0].id").value(jobId))
                .andExpect(jsonPath("$.pagination.totalItems").value(1));
    }

    /**
     * Money the user moves between their own accounts is a transfer, not income on the way in and
     * spending on the way out. The test user is "Test User".
     */
    @Test
    void ownMoneyMovedBetweenAccountsIsImportedAsATransfer() throws Exception {
        uploadAndWait("""
                Date,Description,Amount
                05-09-2026,Received from Mr TEST USER,2500
                05-09-2026,Paid to NETFLIX.COM SI CHARGE,-649
                06-09-2026,Received from Jyoti Shrivastava,1500
                07-09-2026,Received from Test User,70000
                07-09-2026,Paid to 2657,-70000
                """);

        List<Map<String, Object>> rows = jdbc.queryForList("""
                SELECT t.raw_description, t.transaction_type, t.is_transfer, c.category_name
                FROM transactions t LEFT JOIN categories c ON c.id = t.category_id
                WHERE t.user_id = ? ORDER BY t.transaction_date, t.amount""", session.userId());

        assertThat(rows).extracting(r -> r.get("raw_description") + " -> " + r.get("transaction_type"))
                .containsExactlyInAnyOrder(
                        "Received from Mr TEST USER -> transfer",
                        "Paid to NETFLIX.COM SI CHARGE -> debit",
                        "Received from Jyoti Shrivastava -> credit",
                        "Received from Test User -> transfer",
                        "Paid to 2657 -> transfer"); // the other leg of the 70,000 transfer
        assertThat(rows).filteredOn(r -> "transfer".equals(r.get("transaction_type")))
                .allSatisfy(r -> {
                    assertThat(r.get("is_transfer")).isEqualTo(true);
                    assertThat(r.get("category_name")).isEqualTo("Transfers");
                });

        // The income and spending totals leave the transfers out: 1,500 in and 649 out, not 74,000 and
        // 70,649.
        Map<String, Object> totals = jdbc.queryForMap("""
                SELECT COALESCE(SUM(amount) FILTER (WHERE transaction_type = 'credit'), 0) AS income,
                       COALESCE(SUM(amount) FILTER (WHERE transaction_type = 'debit'), 0) AS spending
                FROM transactions WHERE user_id = ?""", session.userId());
        assertThat((BigDecimal) totals.get("income")).isEqualByComparingTo("1500");
        assertThat((BigDecimal) totals.get("spending")).isEqualByComparingTo("649");
    }

    @Test
    void sameFileCannotBeImportedTwiceAndOverlappingRowsAreDuplicates() throws Exception {
        uploadAndWait(STATEMENT);

        upload(session, "again.csv", "text/csv", STATEMENT.getBytes(StandardCharsets.UTF_8), account.getId())
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("DUPLICATE_IMPORT"))
                .andExpect(jsonPath("$.error.details.importJobId").isNotEmpty());

        String overlapping = """
                Txn Date;Narration;Withdrawal;Deposit
                04/09/2026;SWIGGY ORDER;380,50;
                04/09/2026;UPI/ZOMATO/77;450,00;
                10/09/2026;Starbucks MG Road;320,00;
                """;
        String jobId = uploadAndWait(overlapping);
        mockMvc.perform(get("/v1/imports/" + jobId).header("Authorization", session.bearer()))
                .andExpect(jsonPath("$.data.importedCount").value(1))
                .andExpect(jsonPath("$.data.duplicateCount").value(2))
                .andExpect(jsonPath("$.data.invalidCount").value(0));
        mockMvc.perform(get("/v1/imports/" + jobId + "/errors").header("Authorization", session.bearer()))
                .andExpect(jsonPath("$.data[0].errorCode").value("DUPLICATE"));
    }

    @Test
    void rejectsWrongFileTypesBinaryContentAndUnknownStructure() throws Exception {
        upload(session, "photo.png", "image/png", new byte[] {(byte) 0x89, 'P', 'N', 'G'}, account.getId())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_FILE_TYPE"));
        upload(session, "test.txt", "text/plain", "malicious content".getBytes(), account.getId())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_FILE_TYPE"));
        upload(session, "sneaky.csv", "text/csv", new byte[] {'a', 0, 'b'}, account.getId())
                .andExpect(status().isBadRequest());
        upload(session, "empty.csv", "text/csv", new byte[0], account.getId())
                .andExpect(status().isBadRequest());
    }

    @Test
    void rejectsFilesOverFiftyMegabytesWith413() throws Exception {
        byte[] huge = new byte[50 * 1024 * 1024 + 1];
        java.util.Arrays.fill(huge, (byte) 'a');
        upload(session, "huge.csv", "text/csv", huge, account.getId())
                .andExpect(status().isPayloadTooLarge())
                .andExpect(jsonPath("$.error.code").value("FILE_TOO_LARGE"));
    }

    @Test
    void accountAndJobsAreScopedToTheUser() throws Exception {
        Session other = TestAuth.newSession(mockMvc);
        upload(other, "x.csv", "text/csv", STATEMENT.getBytes(StandardCharsets.UTF_8), account.getId())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.message").value("Invalid account ID"));

        String jobId = uploadAndWait(STATEMENT);
        mockMvc.perform(get("/v1/imports/" + jobId).header("Authorization", other.bearer()))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/v1/imports/" + jobId + "/errors").header("Authorization", other.bearer()))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/v1/imports/history").header("Authorization", other.bearer()))
                .andExpect(jsonPath("$.data", hasSize(0)));
    }

    @Test
    void withoutAccountIdAPrimaryAccountIsUsed() throws Exception {
        Session fresh = TestAuth.newSession(mockMvc);
        String body = upload(fresh, "s.csv", "application/vnd.ms-excel",
                "Date,Description,Amount\n05-09-2026,Tea stall,-20\n".getBytes(StandardCharsets.UTF_8), null)
                .andExpect(status().isAccepted())
                .andReturn().getResponse().getContentAsString();
        String jobId = TestAuth.read(body).get("data").get("importJobId").asText();
        session = fresh;
        JsonNode status = waitForCompletion(jobId);
        assertThat(status.get("status").asText()).isEqualTo("completed");
        mockMvc.perform(get("/v1/accounts").header("Authorization", fresh.bearer()))
                .andExpect(jsonPath("$.data", hasSize(1)))
                .andExpect(jsonPath("$.data[0].isPrimary").value(true));
    }
}

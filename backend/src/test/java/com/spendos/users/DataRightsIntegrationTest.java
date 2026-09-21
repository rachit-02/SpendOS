package com.spendos.users;

import static com.spendos.support.TestAuth.json;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.spendos.support.IntegrationTestBase;
import com.spendos.support.TestAuth;
import com.spendos.support.TestAuth.Session;
import com.spendos.support.TestData;
import com.spendos.users.service.AccountPurgeService;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

/** GDPR-style user rights: data export and permanent deletion after the 30-day recovery window. */
class DataRightsIntegrationTest extends IntegrationTestBase {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private TestData testData;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private AccountPurgeService purgeService;

    private static Map<String, String> unzip(byte[] bytes) throws IOException {
        Map<String, String> files = new HashMap<>();
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(bytes))) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                files.put(entry.getName(), new String(zip.readAllBytes(), StandardCharsets.UTF_8));
            }
        }
        return files;
    }

    @Test
    void exportContainsAllOfTheUsersDataAndNothingElse() throws Exception {
        Session session = TestAuth.newSession(mockMvc);
        UUID account = testData.account(session.userId()).getId();
        testData.debit(session.userId(), account, "=HYPERLINK(\"http://evil\")", "Food", "450", LocalDate.now().minusDays(2));
        testData.debit(session.userId(), account, "Zomato", "Food", "320", LocalDate.now().minusDays(1));
        Session other = TestAuth.newSession(mockMvc);
        testData.debit(other.userId(), testData.account(other.userId()).getId(), "Someone Else's Shop", "Food", "999",
                LocalDate.now().minusDays(1));

        byte[] zip = mockMvc.perform(get("/v1/users/me/export").header("Authorization", session.bearer()))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", org.hamcrest.Matchers.startsWith("application/zip")))
                .andExpect(header().string("Content-Disposition", org.hamcrest.Matchers.startsWith("attachment; filename=\"spendos-export-")))
                .andExpect(header().string("Cache-Control", org.hamcrest.Matchers.containsString("no-store")))
                .andReturn().getResponse().getContentAsByteArray();
        Map<String, String> files = unzip(zip);

        assertThat(files).containsKeys("user_profile.json", "accounts.csv", "transactions.csv", "budgets.csv", "goals.csv",
                "insights.csv", "recurring_payments.csv", "merchant_mappings.csv", "imports.csv", "audit_logs.json", "README.txt");
        assertThat(files.get("user_profile.json")).contains(session.userId().toString())
                .doesNotContain("password_hash").doesNotContain("$2a$");
        String transactions = files.get("transactions.csv");
        assertThat(transactions.lines().count()).isEqualTo(3); // header + 2 rows
        assertThat(transactions).contains("Zomato").doesNotContain("Someone Else's Shop");
        // Formula injection is neutralised.
        assertThat(transactions).contains("'=HYPERLINK").doesNotContain(",=HYPERLINK");
        assertThat(files.get("audit_logs.json")).doesNotContain(other.userId().toString());

        Long exports = jdbc.queryForObject("SELECT COUNT(*) FROM audit_logs WHERE user_id = ? AND action = 'export'",
                Long.class, session.userId());
        assertThat(exports).isEqualTo(1);
    }

    @Test
    void deletedAccountsArePurgedAfterThirtyDaysKeepingAnonymousAuditLogs() throws Exception {
        Session leaving = TestAuth.newSession(mockMvc);
        UUID account = testData.account(leaving.userId()).getId();
        String uniqueMerchant = "Purge Test Tailors " + UUID.randomUUID();
        UUID merchantId = testData.debit(leaving.userId(), account, uniqueMerchant, "Shopping", "1200",
                LocalDate.now().minusDays(3)).getMerchantId();
        testData.debit(leaving.userId(), account, "Zomato", "Food", "300", LocalDate.now().minusDays(3));
        mockMvc.perform(get("/v1/users/me/export").header("Authorization", leaving.bearer())).andExpect(status().isOk());
        mockMvc.perform(delete("/v1/users/me").header("Authorization", leaving.bearer())
                        .contentType(MediaType.APPLICATION_JSON).content(json("confirmPassword", TestAuth.PASSWORD)))
                .andExpect(status().isOk());

        Session recent = TestAuth.newSession(mockMvc);
        mockMvc.perform(delete("/v1/users/me").header("Authorization", recent.bearer())
                        .contentType(MediaType.APPLICATION_JSON).content(json("confirmPassword", TestAuth.PASSWORD)))
                .andExpect(status().isOk());

        // Within the recovery window nothing is removed.
        purgeService.purge(LocalDateTime.now(ZoneOffset.UTC));
        assertThat(count("SELECT COUNT(*) FROM users WHERE id = ?", leaving.userId())).isEqualTo(1);

        jdbc.update("UPDATE users SET deleted_at = ? WHERE id = ?",
                java.sql.Timestamp.valueOf(LocalDateTime.now(ZoneOffset.UTC).minusDays(31)), leaving.userId());
        int purged = purgeService.purge(LocalDateTime.now(ZoneOffset.UTC));

        assertThat(purged).isEqualTo(1);
        assertThat(count("SELECT COUNT(*) FROM users WHERE id = ?", leaving.userId())).isZero();
        assertThat(count("SELECT COUNT(*) FROM transactions WHERE user_id = ?", leaving.userId())).isZero();
        assertThat(count("SELECT COUNT(*) FROM accounts WHERE user_id = ?", leaving.userId())).isZero();
        assertThat(count("SELECT COUNT(*) FROM merchants WHERE id = ?", merchantId)).isZero(); // only they used it
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM merchants WHERE merchant_name = 'Zomato'", Long.class))
                .isEqualTo(1); // shared merchants stay
        assertThat(count("SELECT COUNT(*) FROM users WHERE id = ?", recent.userId())).isEqualTo(1);
        // The audit trail survives without pointing at the person.
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM audit_logs WHERE user_id IS NULL AND action = 'export'",
                Long.class)).isPositive();
    }

    private long count(String sql, UUID id) {
        Long value = jdbc.queryForObject(sql, Long.class, id);
        return value == null ? 0 : value;
    }
}

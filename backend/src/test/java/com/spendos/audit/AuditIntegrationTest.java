package com.spendos.audit;

import static com.spendos.support.TestAuth.json;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.spendos.support.IntegrationTestBase;
import com.spendos.support.TestAuth;
import com.spendos.support.TestAuth.Session;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

class AuditIntegrationTest extends IntegrationTestBase {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void accountChangesAreAuditedWithOldValuesAndNeverTheSecret() throws Exception {
        Session session = TestAuth.newSession(mockMvc);
        mockMvc.perform(put("/v1/users/me").header("Authorization", session.bearer())
                        .contentType(MediaType.APPLICATION_JSON).content(json("fullName", "Renamed Person")))
                .andExpect(status().isOk());
        mockMvc.perform(put("/v1/users/me/preferences").header("Authorization", session.bearer())
                        .contentType(MediaType.APPLICATION_JSON).content(json("currencyCode", "USD")))
                .andExpect(status().isOk());

        List<Map<String, Object>> rows = jdbc.queryForList("""
                SELECT entity_type, action, old_values::text AS old_values, new_values::text AS new_values
                FROM audit_logs WHERE user_id = ? ORDER BY created_at""", session.userId());
        assertThat(rows).anySatisfy(row -> {
            assertThat(row.get("entity_type")).isEqualTo("user");
            assertThat(row.get("new_values").toString()).contains("Renamed Person");
            assertThat(row.get("old_values").toString()).contains("fullName");
        });
        assertThat(rows).anySatisfy(row -> {
            assertThat(row.get("entity_type")).isEqualTo("user_preferences");
            assertThat(row.get("old_values").toString()).contains("\"currencyCode\": \"INR\"");
            assertThat(row.get("new_values").toString()).contains("\"currencyCode\": \"USD\"");
        });
        assertThat(rows.toString()).doesNotContain(TestAuth.PASSWORD).doesNotContain("$2a$");
    }

    @Test
    void auditLogsCannotBeEditedOrDeleted() throws Exception {
        Session session = TestAuth.newSession(mockMvc);
        mockMvc.perform(put("/v1/users/me/preferences").header("Authorization", session.bearer())
                        .contentType(MediaType.APPLICATION_JSON).content(json("theme", "dark")))
                .andExpect(status().isOk());

        assertThatThrownBy(() -> jdbc.update("UPDATE audit_logs SET action = 'tampered' WHERE user_id = ?", session.userId()))
                .isInstanceOf(DataAccessException.class).hasMessageContaining("append-only");
        assertThatThrownBy(() -> jdbc.update("DELETE FROM audit_logs WHERE user_id = ?", session.userId()))
                .isInstanceOf(DataAccessException.class).hasMessageContaining("append-only");
    }
}

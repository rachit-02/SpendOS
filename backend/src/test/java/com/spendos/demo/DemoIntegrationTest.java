package com.spendos.demo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.spendos.support.IntegrationTestBase;
import com.spendos.support.TestAuth;
import com.spendos.users.service.AccountPurgeService;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

class DemoIntegrationTest extends IntegrationTestBase {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private AccountPurgeService purgeService;

    private JsonNode data(String token, String url) throws Exception {
        return TestAuth.read(mockMvc.perform(get(url).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8)).get("data");
    }

    private long totalTransactions(String token) throws Exception {
        return TestAuth.read(mockMvc.perform(get("/v1/transactions?pageSize=1").header("Authorization", "Bearer " + token))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8)).at("/pagination/totalItems").asLong();
    }

    private JsonNode startDemo() throws Exception {
        return TestAuth.read(mockMvc.perform(post("/v1/auth/demo"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8)).get("data");
    }

    @Test
    void demoAccountIsSignedInWithRealisticDataForEveryFeature() throws Exception {
        JsonNode demo = startDemo();
        String token = demo.get("accessToken").asText();
        assertThat(demo.at("/user/email").asText()).endsWith("@demo.spendos.invalid");

        assertThat(data(token, "/v1/users/me/preferences").get("demoMode").asBoolean()).isTrue();
        JsonNode transactions = TestAuth.read(mockMvc.perform(get("/v1/transactions?pageSize=1")
                .header("Authorization", "Bearer " + token)).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8));
        assertThat(transactions.at("/pagination/totalItems").asLong()).isGreaterThan(200);
        assertThat(data(token, "/v1/budgets")).hasSize(2);
        assertThat(data(token, "/v1/goals")).hasSize(2);
        assertThat(data(token, "/v1/recurring").findValuesAsText("merchantName")).contains("Netflix", "Spotify");
        JsonNode dashboard = data(token, "/v1/dashboard");
        assertThat(dashboard.at("/summary/totalIncome").decimalValue()).isPositive();
        assertThat(data(token, "/v1/health-metrics").get("score").asInt()).isBetween(1, 100);
    }

    @Test
    void demoCanBeResetButRealAccountsCannot() throws Exception {
        String token = startDemo().get("accessToken").asText();
        JsonNode goals = data(token, "/v1/goals");
        mockMvc.perform(delete("/v1/goals/" + goals.get(0).get("id").asText()).header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());
        long before = totalTransactions(token);

        mockMvc.perform(post("/v1/demo/reset").header("Authorization", "Bearer " + token)).andExpect(status().isOk());
        assertThat(data(token, "/v1/goals")).hasSize(2);
        assertThat(data(token, "/v1/accounts")).hasSize(1);
        assertThat(totalTransactions(token)).isEqualTo(before); // same seed, same data

        var real = TestAuth.newSession(mockMvc);
        mockMvc.perform(post("/v1/demo/reset").header("Authorization", real.bearer())).andExpect(status().isForbidden());
    }

    @Test
    void demoAccountsArePurgedAfterADay() throws Exception {
        JsonNode demo = startDemo();
        UUID userId = UUID.fromString(demo.at("/user/userId").asText());
        purgeService.purge(LocalDateTime.now(ZoneOffset.UTC));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM users WHERE id = ?", Long.class, userId)).isEqualTo(1);

        jdbc.update("UPDATE users SET created_at = ? WHERE id = ?",
                Timestamp.valueOf(LocalDateTime.now(ZoneOffset.UTC).minusDays(2)), userId);
        purgeService.purge(LocalDateTime.now(ZoneOffset.UTC));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM users WHERE id = ?", Long.class, userId)).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM transactions WHERE user_id = ?", Long.class, userId)).isZero();
    }
}

package com.spendos.health;

import static com.spendos.support.TestAuth.json;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.spendos.health.repository.FinancialHealthMetricsRepository;
import com.spendos.support.IntegrationTestBase;
import com.spendos.support.TestAuth;
import com.spendos.support.TestAuth.Session;
import com.spendos.support.TestData;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.YearMonth;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

class HealthMetricsIntegrationTest extends IntegrationTestBase {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private TestData testData;

    @Autowired
    private FinancialHealthMetricsRepository metricsRepository;

    @Autowired
    private JdbcTemplate jdbc;

    private Session session;
    private final YearMonth thisMonth = YearMonth.now();

    @BeforeEach
    void setUp() throws Exception {
        session = TestAuth.newSession(mockMvc);
        var account = testData.account(session.userId()).getId();
        // Three months before this one: saving well, then overspending last month.
        String[] spend = {"45000", "50000", "80000"};
        for (int m = 3; m >= 1; m--) {
            YearMonth month = thisMonth.minusMonths(m);
            testData.credit(session.userId(), account, "Acme Corp", "Income", "75000", month.atDay(1));
            testData.debit(session.userId(), account, "Landlord", "Bills", spend[3 - m], month.atDay(3));
        }
    }

    private JsonNode data(String url) throws Exception {
        String body = mockMvc.perform(get(url).header("Authorization", session.bearer()))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        return TestAuth.read(body).get("data");
    }

    @Test
    void currentScoreHasFactorsThatAddUpAndIsStored() throws Exception {
        JsonNode metrics = data("/v1/health-metrics");

        assertThat(metrics.get("enabled").asBoolean()).isTrue();
        int score = metrics.get("score").asInt();
        assertThat(score).isBetween(0, 100);
        BigDecimal earned = BigDecimal.ZERO;
        int weight = 0;
        for (JsonNode factor : metrics.get("factors")) {
            if (factor.get("scored").asBoolean()) {
                earned = earned.add(factor.get("points").decimalValue());
                weight += factor.get("weight").asInt();
            }
        }
        // Score is earned points normalised over the factors that could be scored.
        assertThat(earned.multiply(BigDecimal.valueOf(100)).divide(BigDecimal.valueOf(weight), 0, java.math.RoundingMode.HALF_UP)
                .intValue()).isEqualTo(score);
        assertThat(metrics.at("/metrics/averageMonthlyIncome").decimalValue()).isPositive();
        assertThat(metrics.get("recommendations")).isNotEmpty();
        assertThat(metrics.at("/recommendations/0/action").asText()).isNotBlank();

        assertThat(metricsRepository.findByUserId(session.userId())).hasValueSatisfying(row ->
                assertThat(row.getHealthScore()).isEqualTo(score));
    }

    @Test
    void historyTracksMonthlyScoresFromTheFirstMonthWithData() throws Exception {
        JsonNode history = data("/v1/health-metrics/history?months=6");

        assertThat(history.size()).isBetween(3, 4); // months before the first transaction are skipped
        assertThat(history.get(0).get("period").asText()).isEqualTo(thisMonth.minusMonths(3).toString());
        Integer stored = jdbc.queryForObject("SELECT COUNT(*) FROM financial_health_history WHERE user_id = ?",
                Integer.class, session.userId());
        assertThat(stored).isEqualTo(history.size());

        mockMvc.perform(get("/v1/health-metrics/history?months=0").header("Authorization", session.bearer()))
                .andExpect(status().isBadRequest());
    }

    @Test
    void explainsWhatChangedLastMonth() throws Exception {
        YearMonth last = thisMonth.minusMonths(1);
        JsonNode explanation = data("/v1/health-metrics/explanation?month=" + last.getMonthValue() + "&year=" + last.getYear());

        assertThat(explanation.get("period").asText()).isEqualTo(last.toString());
        assertThat(explanation.get("change").asInt()).isNegative();
        assertThat(explanation.get("summary").asText()).startsWith("Your score fell from");
        assertThat(explanation.get("changes")).anySatisfy(change -> {
            assertThat(change.get("factor").asText()).isEqualTo("savingsRate");
            assertThat(change.get("change").decimalValue()).isNegative();
            assertThat(change.get("reason").asText()).contains("of your income, compared with");
        });
    }

    @Test
    void respectsTheSettingToTurnTheScoreOff() throws Exception {
        mockMvc.perform(put("/v1/users/me/preferences").header("Authorization", session.bearer())
                        .contentType(MediaType.APPLICATION_JSON).content(json("financialHealthScoreEnabled", false)))
                .andExpect(status().isOk());

        JsonNode metrics = data("/v1/health-metrics");
        assertThat(metrics.get("enabled").asBoolean()).isFalse();
        assertThat(metrics.has("score")).isFalse();
        assertThat(data("/v1/health-metrics/history")).isEmpty();
    }
}

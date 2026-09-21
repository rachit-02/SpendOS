package com.spendos.insights;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.spendos.support.IntegrationTestBase;
import com.spendos.support.TestAuth;
import com.spendos.support.TestAuth.Session;
import com.spendos.support.TestData;
import com.spendos.transactions.domain.Account;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;

class InsightIntegrationTest extends IntegrationTestBase {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private TestData testData;

    private Session session;
    private Account account;
    private final YearMonth lastMonth = YearMonth.now().minusMonths(1);

    @BeforeEach
    void setUp() throws Exception {
        session = TestAuth.newSession(mockMvc);
        account = testData.account(session.userId());
        // Four baseline months before "last month": modest food delivery and one-off shopping
        for (int m = 1; m <= 4; m++) {
            YearMonth month = lastMonth.minusMonths(m);
            for (int i = 0; i < 6; i++) {
                testData.debit(session.userId(), account.getId(), "Zomato", "Food", "300", month.atDay(2 + i * 4));
            }
            testData.debit(session.userId(), account.getId(), "Amazon", "Shopping", String.valueOf(1500 + m * 100),
                    month.atDay(10));
            testData.debit(session.userId(), account.getId(), "Amazon", "Shopping", "1700", month.atDay(20));
        }
        // Last month: food delivery doubles in frequency and a large electronics purchase
        for (int i = 0; i < 16; i++) {
            testData.debit(session.userId(), account.getId(), "Zomato", "Food", "300", lastMonth.atDay(1 + i));
        }
        testData.debit(session.userId(), account.getId(), "Amazon", "Shopping", "8500", lastMonth.atDay(18));
    }

    private JsonNode data(Session who, String url) throws Exception {
        return TestAuth.read(mockMvc.perform(get(url).header("Authorization", who.bearer()))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString()).get("data");
    }

    @Test
    void insightsAreDetectedRankedAndExplained() throws Exception {
        JsonNode insights = data(session, "/v1/insights?period=last_month");

        assertThat(insights).extracting(n -> n.get("type").asText())
                .contains("money_leak", "anomaly", "opportunity", "spending_trend");
        BigDecimal previous = new BigDecimal("1000");
        for (JsonNode insight : insights) {
            assertThat(insight.get("importance").decimalValue()).isLessThanOrEqualTo(previous);
            previous = insight.get("importance").decimalValue();
            assertThat(insight.get("description").asText()).isNotBlank();
        }

        JsonNode leak = null;
        for (JsonNode insight : insights) {
            if ("money_leak".equals(insight.get("type").asText())) {
                leak = insight;
            }
        }
        assertThat(leak).isNotNull();
        assertThat(leak.get("description").asText()).startsWith("You spent ₹4,800 across 16 small purchases at Zomato");
        assertThat(leak.get("merchantName").asText()).isEqualTo("Zomato");
        assertThat(leak.get("suggestedAction").asText()).isNotBlank();

        JsonNode detail = data(session, "/v1/insights/" + leak.get("id").asText());
        assertThat(detail.get("relatedTransactions")).hasSize(16);
        assertThat(detail.at("/relatedTransactions/0/merchant").asText()).isEqualTo("Zomato");
    }

    @Test
    void largePurchaseAnomalyAndLeakEndpoints() throws Exception {
        JsonNode insights = data(session, "/v1/insights?type=anomaly&period=last_month");
        assertThat(insights).anySatisfy(a -> assertThat(a.get("description").asText())
                .contains("₹8,500 at Amazon on Shopping"));

        assertThat(data(session, "/v1/insights/leaks?period=last_month")).hasSize(1);
        assertThat(data(session, "/v1/insights/opportunities?period=last_month")).isNotEmpty();

        mockMvc.perform(get("/v1/insights?type=bogus").header("Authorization", session.bearer()))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/v1/insights?period=2999-01").header("Authorization", session.bearer()))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/v1/insights/anomalies?sensitivityLevel=extreme").header("Authorization", session.bearer()))
                .andExpect(status().isBadRequest());
    }

    @Test
    void liveAnomaliesUseTheCurrentMonth() throws Exception {
        LocalDate today = LocalDate.now();
        testData.debit(session.userId(), account.getId(), "Croma", "Shopping", "30000", today);

        JsonNode anomalies = data(session, "/v1/insights/anomalies?sensitivityLevel=high&categoryId="
                + testData.categoryId("Shopping"));

        assertThat(anomalies).isNotEmpty();
        assertThat(anomalies).anySatisfy(a -> {
            assertThat(a.get("categoryName").asText()).isEqualTo("Shopping");
            assertThat(a.get("period").asText()).isEqualTo("current_month");
            assertThat(a.get("relatedTransactions")).isNotEmpty();
        });
    }

    @Test
    void insightsArePrivateAndAppearInHistoryAndDashboard() throws Exception {
        JsonNode insights = data(session, "/v1/insights?period=last_month");
        String id = insights.get(0).get("id").asText();

        Session other = TestAuth.newSession(mockMvc);
        mockMvc.perform(get("/v1/insights/" + id).header("Authorization", other.bearer())).andExpect(status().isNotFound());
        assertThat(data(other, "/v1/insights?period=last_month")).isEmpty();

        assertThat(data(session, "/v1/insights/history?months=3")).isNotEmpty();

        JsonNode dashboard = data(session, "/v1/dashboard?month=" + lastMonth.getMonthValue() + "&year=" + lastMonth.getYear());
        assertThat(dashboard.get("insights").size()).isBetween(1, 3);

        mockMvc.perform(post("/v1/insights/generate?period=last_month").header("Authorization", session.bearer()))
                .andExpect(status().isOk());
    }
}

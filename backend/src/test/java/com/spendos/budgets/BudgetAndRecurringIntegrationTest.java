package com.spendos.budgets;

import static com.spendos.support.TestAuth.json;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.spendos.support.IntegrationTestBase;
import com.spendos.support.TestAuth;
import com.spendos.support.TestAuth.Session;
import com.spendos.support.TestData;
import com.spendos.transactions.domain.Account;
import com.spendos.transactions.repository.TransactionRepository;
import java.time.LocalDate;
import java.time.YearMonth;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

class BudgetAndRecurringIntegrationTest extends IntegrationTestBase {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private TestData testData;

    @Autowired
    private TransactionRepository transactionRepository;

    private Session session;
    private Account account;
    private final YearMonth month = YearMonth.now();

    @BeforeEach
    void setUp() throws Exception {
        session = TestAuth.newSession(mockMvc);
        account = testData.account(session.userId());
    }

    private String budgetJson(String name, String total, String foodAllocation) {
        return "{\"budgetName\":\"" + name + "\",\"budgetType\":\"monthly\",\"totalAmount\":" + total
                + ",\"startDate\":\"" + month.atDay(1) + "\",\"alertThreshold\":90,\"categories\":[{\"categoryId\":\""
                + testData.categoryId("Food") + "\",\"allocatedAmount\":" + foodAllocation + "}]}";
    }

    private JsonNode data(String url) throws Exception {
        return TestAuth.read(mockMvc.perform(get(url).header("Authorization", session.bearer()))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString()).get("data");
    }

    @Test
    void budgetProgressAndAlertAtNinetyPercent() throws Exception {
        testData.debit(session.userId(), account.getId(), "Zomato", "Food", "8400", month.atDay(1));
        testData.debit(session.userId(), account.getId(), "Uber", "Transport", "500", month.atDay(1));

        String body = mockMvc.perform(post("/v1/budgets").header("Authorization", session.bearer())
                        .contentType(MediaType.APPLICATION_JSON).content(budgetJson("Food Budget", "9000", "9000")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.endDate").value(month.atEndOfMonth().toString()))
                .andExpect(jsonPath("$.data.spentAmount").value(8400.0))
                .andExpect(jsonPath("$.data.percentage").value(93.3))
                .andExpect(jsonPath("$.data.remainingAmount").value(600.0))
                .andExpect(jsonPath("$.data.isAlert").value(true))
                .andExpect(jsonPath("$.data.isExceeded").value(false))
                .andExpect(jsonPath("$.data.status").value("warning"))
                .andExpect(jsonPath("$.data.categories[0].categoryName").value("Food"))
                .andReturn().getResponse().getContentAsString();
        String budgetId = TestAuth.read(body).get("data").get("id").asText();

        JsonNode alerts = data("/v1/budgets/alerts");
        assertThat(alerts).isNotEmpty();
        assertThat(alerts.get(0).get("level").asText()).isEqualTo("warning");
        assertThat(alerts.get(0).get("message").asText()).contains("93% of its budget");

        JsonNode progress = data("/v1/budgets/" + budgetId + "/progress");
        assertThat(progress.get("daysTotal").asInt()).isEqualTo(month.lengthOfMonth());
        assertThat(progress.get("daysElapsed").asInt()).isEqualTo(LocalDate.now().getDayOfMonth());
        assertThat(progress.get("pace").asText()).isIn("ahead", "on_track", "behind");

        // Spending more tips it over
        testData.debit(session.userId(), account.getId(), "Swiggy", "Food", "700", month.atDay(1));
        mockMvc.perform(get("/v1/budgets/" + budgetId).header("Authorization", session.bearer()))
                .andExpect(jsonPath("$.data.isExceeded").value(true))
                .andExpect(jsonPath("$.data.status").value("exceeded"));

        JsonNode dashboard = data("/v1/dashboard");
        assertThat(dashboard.at("/budgets/0/budgetName").asText()).isEqualTo("Food Budget");
        assertThat(dashboard.at("/financialHealth/breakdown").toString()).contains("budgetAdherence");
    }

    @Test
    void budgetValidationUpdateDeleteAndIsolation() throws Exception {
        mockMvc.perform(post("/v1/budgets").header("Authorization", session.bearer())
                        .contentType(MediaType.APPLICATION_JSON).content(budgetJson("Too much", "5000", "9000")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_AMOUNT"));
        mockMvc.perform(post("/v1/budgets").header("Authorization", session.bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json("budgetName", "Custom", "budgetType", "custom", "totalAmount", 100,
                                "startDate", "2026-09-10", "endDate", "2026-09-01")))
                .andExpect(status().isBadRequest());

        String body = mockMvc.perform(post("/v1/budgets").header("Authorization", session.bearer())
                        .contentType(MediaType.APPLICATION_JSON).content(budgetJson("Monthly", "50000", "9000")))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        String id = TestAuth.read(body).get("data").get("id").asText();

        mockMvc.perform(put("/v1/budgets/" + id).header("Authorization", session.bearer())
                        .contentType(MediaType.APPLICATION_JSON).content(budgetJson("Renamed", "60000", "10000")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.budgetName").value("Renamed"))
                .andExpect(jsonPath("$.data.categories[0].allocatedAmount").value(10000.0));

        Session other = TestAuth.newSession(mockMvc);
        mockMvc.perform(get("/v1/budgets/" + id).header("Authorization", other.bearer())).andExpect(status().isNotFound());
        mockMvc.perform(delete("/v1/budgets/" + id).header("Authorization", other.bearer())).andExpect(status().isNotFound());

        mockMvc.perform(delete("/v1/budgets/" + id).header("Authorization", session.bearer())).andExpect(status().isNoContent());
        assertThat(data("/v1/budgets")).isEmpty();
    }

    @Test
    void recurringNetflixIsDetectedConfirmedAndFlagged() throws Exception {
        LocalDate last = LocalDate.now().minusDays(3);
        for (int i = 0; i < 6; i++) {
            testData.debit(session.userId(), account.getId(), "Netflix", "Subscriptions", "499", last.minusMonths(i));
        }
        for (int i = 0; i < 4; i++) {
            testData.debit(session.userId(), account.getId(), "Zomato", "Food", String.valueOf(200 + i * 150),
                    last.minusDays(i * 3L));
        }

        mockMvc.perform(post("/v1/recurring/detect").header("Authorization", session.bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.detected").value(1));

        JsonNode list = data("/v1/recurring");
        assertThat(list).hasSize(1);
        JsonNode netflix = list.get(0);
        assertThat(netflix.get("merchantName").asText()).isEqualTo("Netflix");
        assertThat(netflix.get("frequency").asText()).isEqualTo("monthly");
        assertThat(netflix.get("typicalAmount").decimalValue()).isEqualByComparingTo("499");
        assertThat(netflix.get("confidence").decimalValue()).isGreaterThanOrEqualTo(new java.math.BigDecimal("0.9"));
        assertThat(netflix.get("nextExpectedDate").asText()).isEqualTo(last.plusMonths(1).toString());
        assertThat(netflix.get("status").asText()).isEqualTo("pending");
        assertThat(transactionRepository.findByUserIdAndTransactionDateBetween(session.userId(), last.minusYears(1), last)
                .stream().filter(t -> t.isRecurring()).count()).isEqualTo(6);

        String id = netflix.get("id").asText();
        mockMvc.perform(post("/v1/recurring/" + id + "/confirm").header("Authorization", session.bearer()))
                .andExpect(jsonPath("$.data.isUserConfirmed").value(true));
        assertThat(data("/v1/recurring?confirmed=true")).hasSize(1);

        JsonNode dashboard = data("/v1/dashboard");
        assertThat(dashboard.at("/recurringPayments/0/merchantName").asText()).isEqualTo("Netflix");

        // Dismissing hides it and a new detection run does not bring it back
        mockMvc.perform(post("/v1/recurring/" + id + "/reject").header("Authorization", session.bearer()))
                .andExpect(jsonPath("$.data.isActive").value(false));
        mockMvc.perform(post("/v1/recurring/detect").header("Authorization", session.bearer())).andExpect(status().isOk());
        assertThat(data("/v1/recurring")).isEmpty();
        assertThat(data("/v1/recurring?confirmed=dismissed")).hasSize(1);
        assertThat(transactionRepository.findByUserIdAndTransactionDateBetween(session.userId(), last.minusYears(1), last)
                .stream().noneMatch(t -> t.isRecurring())).isTrue();

        Session other = TestAuth.newSession(mockMvc);
        mockMvc.perform(post("/v1/recurring/" + id + "/confirm").header("Authorization", other.bearer()))
                .andExpect(status().isNotFound());
    }

    @Test
    void upcomingRecurringWithinWindow() throws Exception {
        LocalDate last = LocalDate.now().minusDays(20);
        for (int i = 0; i < 4; i++) {
            testData.debit(session.userId(), account.getId(), "Spotify", "Subscriptions", "119", last.minusMonths(i));
        }
        mockMvc.perform(post("/v1/recurring/detect").header("Authorization", session.bearer())).andExpect(status().isOk());

        assertThat(data("/v1/recurring/upcoming?days=30")).hasSize(1);
        assertThat(data("/v1/recurring/upcoming?days=5")).isEmpty();
    }
}

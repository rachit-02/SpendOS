package com.spendos.analytics;

import static com.spendos.support.TestAuth.json;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
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
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

class DashboardIntegrationTest extends IntegrationTestBase {

    // A complete past month so "today" does not truncate the period.
    private static final YearMonth MONTH = YearMonth.of(2026, 8);
    private static final YearMonth PREVIOUS = MONTH.minusMonths(1);

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private TestData testData;

    private Session session;
    private Account account;

    @BeforeEach
    void setUp() throws Exception {
        session = TestAuth.newSession(mockMvc);
        account = testData.account(session.userId());
        LocalDate d = MONTH.atDay(1);
        testData.credit(session.userId(), account.getId(), "Acme Corp", "Income", "75000", d);
        testData.debit(session.userId(), account.getId(), "Zomato", "Food", "5000", d.plusDays(2));
        testData.debit(session.userId(), account.getId(), "Zomato", "Food", "3400", d.plusDays(9));
        testData.debit(session.userId(), account.getId(), "Uber", "Transport", "3600", d.plusDays(4));
        testData.debit(session.userId(), account.getId(), "Landlord", "Bills", "25000", d.plusDays(5));
        // previous month: less food, more transport
        LocalDate p = PREVIOUS.atDay(3);
        testData.credit(session.userId(), account.getId(), "Acme Corp", "Income", "75000", p);
        testData.debit(session.userId(), account.getId(), "Zomato", "Food", "6000", p);
        testData.debit(session.userId(), account.getId(), "Uber", "Transport", "6000", p);
        testData.debit(session.userId(), account.getId(), "Landlord", "Bills", "25000", p);
    }

    private JsonNode dashboard(Session who, YearMonth month) throws Exception {
        String body = mockMvc.perform(get("/v1/dashboard?month=" + month.getMonthValue() + "&year=" + month.getYear())
                        .header("Authorization", who.bearer()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return TestAuth.read(body).get("data");
    }

    @Test
    void summaryTotalsMatchTheMonthsTransactions() throws Exception {
        JsonNode data = dashboard(session, MONTH);

        assertThat(data.at("/period/month").asText()).isEqualTo("August");
        assertThat(data.at("/period/endDate").asText()).isEqualTo("2026-08-31");
        assertThat(data.at("/summary/totalIncome").decimalValue()).isEqualByComparingTo("75000");
        assertThat(data.at("/summary/totalExpense").decimalValue()).isEqualByComparingTo("37000");
        assertThat(data.at("/summary/netSavings").decimalValue()).isEqualByComparingTo("38000");
        assertThat(data.at("/summary/savingsRate").decimalValue()).isEqualByComparingTo("0.507");
        assertThat(data.at("/summary/transactionCount").asInt()).isEqualTo(5);
        // 37000 vs 37000 last month
        assertThat(data.at("/summary/expenseChangePercentage").decimalValue()).isEqualByComparingTo("0");
    }

    @Test
    void categoryBreakdownIsOrderedSumsToHundredAndShowsTrends() throws Exception {
        JsonNode categories = dashboard(session, MONTH).at("/spending/byCategory");

        assertThat(categories).hasSize(3);
        assertThat(categories.get(0).get("categoryName").asText()).isEqualTo("Bills");
        BigDecimal sum = BigDecimal.ZERO;
        for (JsonNode category : categories) {
            sum = sum.add(category.get("percentage").decimalValue());
        }
        assertThat(sum).isEqualByComparingTo("100.0");

        JsonNode food = find(categories, "Food");
        assertThat(food.get("amount").decimalValue()).isEqualByComparingTo("8400");
        assertThat(food.get("trend").asText()).isEqualTo("up");       // 6000 -> 8400
        assertThat(find(categories, "Transport").get("trend").asText()).isEqualTo("down"); // 6000 -> 3600
        assertThat(find(categories, "Bills").get("trend").asText()).isEqualTo("stable");
    }

    @Test
    void topMerchantsTrendAndRecentTransactions() throws Exception {
        JsonNode data = dashboard(session, MONTH);

        assertThat(data.at("/spending/topMerchants/0/merchantName").asText()).isEqualTo("Landlord");
        JsonNode zomato = data.at("/spending/topMerchants/1");
        assertThat(zomato.get("merchantName").asText()).isEqualTo("Zomato");
        assertThat(zomato.get("amount").decimalValue()).isEqualByComparingTo("8400");
        assertThat(zomato.get("count").asInt()).isEqualTo(2);

        JsonNode trend = data.get("trend");
        assertThat(trend).hasSize(6);
        assertThat(trend.get(5).get("month").asText()).isEqualTo("Aug");
        assertThat(trend.get(5).get("expense").decimalValue()).isEqualByComparingTo("37000");
        assertThat(trend.get(4).get("expense").decimalValue()).isEqualByComparingTo("37000");

        assertThat(data.get("recentTransactions").get(0).get("transactionDate").asText()).isEqualTo("2026-08-10");
    }

    @Test
    void healthScoreIsExplainable() throws Exception {
        JsonNode health = dashboard(session, MONTH).get("financialHealth");

        assertThat(health.get("score").asInt()).isBetween(0, 100);
        assertThat(health.get("factors").has("savingsRate")).isTrue();
        assertThat(health.get("breakdown")).hasSize(5);
        assertThat(health.get("summary").asText()).isNotBlank();
    }

    @Test
    void newTransactionIsReflectedImmediatelyDespiteCaching() throws Exception {
        dashboard(session, MONTH);
        mockMvc.perform(post("/v1/transactions").header("Authorization", session.bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json("accountId", account.getId(), "merchantName", "Cafe", "amount", 500,
                                "transactionType", "debit", "transactionDate", "2026-08-20",
                                "categoryId", testData.categoryId("Food"))))
                .andExpect(status().isCreated());

        assertThat(dashboard(session, MONTH).at("/summary/totalExpense").decimalValue()).isEqualByComparingTo("37500");
    }

    @Test
    void emptyAccountAndValidationAndIsolation() throws Exception {
        Session fresh = TestAuth.newSession(mockMvc);
        JsonNode empty = dashboard(fresh, MONTH);
        assertThat(empty.at("/summary/totalExpense").decimalValue()).isEqualByComparingTo("0");
        assertThat(empty.at("/spending/byCategory")).isEmpty();
        assertThat(empty.at("/financialHealth/score").isMissingNode() || empty.at("/financialHealth/score").isNull()).isTrue();

        mockMvc.perform(get("/v1/dashboard?month=13&year=2026").header("Authorization", session.bearer()))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/v1/dashboard?month=8").header("Authorization", session.bearer()))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/v1/dashboard").header("Authorization", session.bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.period.isCurrentMonth").value(true))
                .andExpect(jsonPath("$.data.trend", hasSize(6)));
        mockMvc.perform(get("/v1/dashboard")).andExpect(status().isUnauthorized());
    }

    private static JsonNode find(JsonNode categories, String name) {
        for (JsonNode category : categories) {
            if (name.equals(category.get("categoryName").asText())) {
                return category;
            }
        }
        throw new AssertionError("No category " + name);
    }
}

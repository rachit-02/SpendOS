package com.spendos.reports;

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
import java.nio.charset.StandardCharsets;
import java.time.YearMonth;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

class AutopsyIntegrationTest extends IntegrationTestBase {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private TestData testData;

    private Session session;
    private final YearMonth month = YearMonth.now().minusMonths(1);
    private String query;

    @BeforeEach
    void setUp() throws Exception {
        session = TestAuth.newSession(mockMvc);
        Account account = testData.account(session.userId());
        YearMonth before = month.minusMonths(1);
        testData.credit(session.userId(), account.getId(), "Acme Corp", "Income", "75000", before.atDay(1));
        testData.debit(session.userId(), account.getId(), "Zomato", "Food", "6000", before.atDay(3));
        testData.debit(session.userId(), account.getId(), "Uber", "Transport", "4400", before.atDay(4));

        testData.credit(session.userId(), account.getId(), "Acme Corp", "Income", "75000", month.atDay(1));
        testData.debit(session.userId(), account.getId(), "Zomato", "Food", "8400", month.atDay(3));
        testData.debit(session.userId(), account.getId(), "Uber", "Transport", "3600", month.atDay(4));
        testData.debit(session.userId(), account.getId(), "Landlord", "Bills", "25000", month.atDay(5));
        query = "month=" + month.getMonthValue() + "&year=" + month.getYear();

        mockMvc.perform(post("/v1/budgets").header("Authorization", session.bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"budgetName\":\"Food\",\"budgetType\":\"monthly\",\"totalAmount\":8000,\"startDate\":\""
                                + month.atDay(1) + "\",\"categories\":[{\"categoryId\":\"" + testData.categoryId("Food")
                                + "\",\"allocatedAmount\":8000}]}"))
                .andExpect(status().isCreated());
    }

    private JsonNode autopsy() throws Exception {
        return TestAuth.read(mockMvc.perform(get("/v1/reports/monthly-autopsy?" + query)
                        .header("Authorization", session.bearer()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8)).get("data");
    }

    @Test
    void autopsySummarizesTheMonth() throws Exception {
        JsonNode report = autopsy();

        assertThat(report.get("isComplete").asBoolean()).isTrue();
        assertThat(report.get("income").decimalValue()).isEqualByComparingTo("75000");
        assertThat(report.get("expenses").decimalValue()).isEqualByComparingTo("37000");
        assertThat(report.get("savings").decimalValue()).isEqualByComparingTo("38000");
        assertThat(report.get("savingsRate").decimalValue()).isEqualByComparingTo("0.507");

        JsonNode increases = report.at("/changes/largestIncreases");
        assertThat(increases.get(0).get("category").asText()).isEqualTo("Bills");
        assertThat(increases.get(1).get("category").asText()).isEqualTo("Food");
        assertThat(increases.get(1).get("amount").decimalValue()).isEqualByComparingTo("2400");
        assertThat(increases.get(1).get("percentageChange").decimalValue()).isEqualByComparingTo("40.0");
        assertThat(report.at("/changes/largestDecreases/0/category").asText()).isEqualTo("Transport");

        assertThat(report.at("/largestMerchants/0/merchantName").asText()).isEqualTo("Landlord");
        JsonNode food = report.at("/budgetPerformance/Food");
        assertThat(food.get("exceeded").asBoolean()).isTrue();
        assertThat(food.get("percentage").decimalValue()).isEqualByComparingTo("105.0");

        assertThat(report.get("mostImportantInsight").asText()).isNotBlank();
        assertThat(report.get("suggestedAction").asText()).isNotBlank();
        assertThat(report.get("nextMonthWatchlist").toString()).contains("over your Food budget");
    }

    @Test
    void reportIsStoredListedAndRenderedAsPdf() throws Exception {
        autopsy();
        JsonNode list = TestAuth.read(mockMvc.perform(get("/v1/reports/autopsies").header("Authorization", session.bearer()))
                .andReturn().getResponse().getContentAsString()).get("data");
        assertThat(list).hasSize(1);
        assertThat(list.get(0).get("month").asInt()).isEqualTo(month.getMonthValue());

        byte[] pdf = mockMvc.perform(get("/v1/reports/monthly-autopsy/pdf?" + query).header("Authorization", session.bearer()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsByteArray();
        assertThat(new String(pdf, 0, 5, StandardCharsets.US_ASCII)).isEqualTo("%PDF-");
        assertThat(pdf.length).isGreaterThan(1500);

        mockMvc.perform(post("/v1/reports/generate-autopsy?" + query).header("Authorization", session.bearer()))
                .andExpect(status().isOk());
    }

    @Test
    void reportsArePrivateAndFutureMonthsRejected() throws Exception {
        autopsy();
        Session other = TestAuth.newSession(mockMvc);
        JsonNode theirs = TestAuth.read(mockMvc.perform(get("/v1/reports/monthly-autopsy?" + query)
                .header("Authorization", other.bearer())).andReturn().getResponse().getContentAsString()).get("data");
        assertThat(theirs.get("expenses").decimalValue()).isEqualByComparingTo("0");

        YearMonth future = YearMonth.now().plusMonths(1);
        mockMvc.perform(post("/v1/reports/generate-autopsy?month=" + future.getMonthValue() + "&year=" + future.getYear())
                        .header("Authorization", session.bearer()))
                .andExpect(status().isBadRequest());
    }
}

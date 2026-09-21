package com.spendos.analytics;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.spendos.analytics.service.AnalyticsService;
import com.spendos.support.IntegrationTestBase;
import com.spendos.support.TestAuth;
import com.spendos.support.TestAuth.Session;
import com.spendos.support.TestData;
import com.spendos.transactions.domain.Account;
import com.spendos.transactions.domain.Transaction;
import com.spendos.transactions.repository.TransactionRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;

class AnalyticsIntegrationTest extends IntegrationTestBase {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private TestData testData;

    @Autowired
    private TransactionRepository transactionRepository;

    private Session session;
    private Account account;
    private final YearMonth thisMonth = YearMonth.now();
    private final YearMonth lastMonth = thisMonth.minusMonths(1);

    @BeforeEach
    void setUp() throws Exception {
        session = TestAuth.newSession(mockMvc);
        account = testData.account(session.userId());
        // Food over six months: 7200, 7800, 6900, 7500, 7300, 8400 (current month)
        int[] food = {7200, 7800, 6900, 7500, 7300, 8400};
        for (int i = 0; i < food.length; i++) {
            YearMonth month = thisMonth.minusMonths(5 - i);
            testData.debit(session.userId(), account.getId(), "Zomato", "Food", String.valueOf(food[i]), month.atDay(1));
        }
        LocalDate lastMonthDay = lastMonth.atDay(5);
        testData.credit(session.userId(), account.getId(), "Acme Corp", "Income", "75000", lastMonthDay);
        Transaction uber = testData.debit(session.userId(), account.getId(), "Uber", "Transport", "3600", lastMonthDay);
        uber.setPaymentMethod("card");
        transactionRepository.save(uber);
        testData.debit(session.userId(), account.getId(), "Landlord", "Bills", "25000", lastMonthDay);
        // Same month a year earlier, for year-over-year
        testData.debit(session.userId(), account.getId(), "Landlord", "Bills", "20000", lastMonth.minusYears(1).atDay(5));
    }

    private JsonNode get(String url) throws Exception {
        String body = mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get(url)
                        .header("Authorization", session.bearer()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return TestAuth.read(body).get("data");
    }

    @Test
    void monthlyAnalyticsBreaksDownIncomeSpendingAndChanges() throws Exception {
        JsonNode data = get("/v1/analytics/monthly?month=" + lastMonth.getMonthValue() + "&year=" + lastMonth.getYear());

        assertThat(data.at("/income/total").decimalValue()).isEqualByComparingTo("75000");
        assertThat(data.at("/income/bySource/0/source").asText()).isEqualTo("Income");
        assertThat(data.at("/expenses/total").decimalValue()).isEqualByComparingTo("35900");
        assertThat(data.at("/savings").decimalValue()).isEqualByComparingTo("39100");
        assertThat(data.at("/expenses/byCategory/0/categoryName").asText()).isEqualTo("Bills");

        BigDecimal sum = BigDecimal.ZERO;
        for (JsonNode category : data.at("/expenses/byCategory")) {
            sum = sum.add(category.get("percentage").decimalValue());
        }
        assertThat(sum).isEqualByComparingTo("100.0");

        assertThat(data.at("/expenses/byPaymentMethod")).extracting(n -> n.get("method").asText())
                .containsExactlyInAnyOrder("upi", "card");
        // Food 7500 -> 7300 two months ago -> last month; Bills and Transport are new
        JsonNode changes = data.at("/previousMonthComparison/categoryChanges");
        assertThat(changes.get(0).get("categoryName").asText()).isEqualTo("Bills");
        assertThat(changes.get(0).has("changePercentage")).as("new category has no percentage change").isFalse();
    }

    @Test
    void categoryTrendHasAverageChangeAndForecast() throws Exception {
        UUID food = testData.categoryId("Food");
        JsonNode data = get("/v1/analytics/categories/trends?categoryId=" + food + "&months=6");

        assertThat(data.get("categoryName").asText()).isEqualTo("Food");
        assertThat(data.get("trend")).hasSize(6);
        assertThat(data.at("/trend/5/amount").decimalValue()).isEqualByComparingTo("8400");
        assertThat(data.at("/trend/5/count").asInt()).isEqualTo(1);
        assertThat(data.get("average").decimalValue()).isEqualByComparingTo("7516.67");
        // 8400 vs average of the previous five (7340) = +14.4%
        assertThat(data.get("percentageChange").decimalValue()).isEqualByComparingTo("14.4");
        // (8400*3 + 7300*2 + 7500*1) / 6 = 7883.33
        assertThat(data.get("forecast").decimalValue()).isEqualByComparingTo("7883.33");

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .get("/v1/analytics/categories/trends?categoryId=" + food + "&months=99")
                        .header("Authorization", session.bearer()))
                .andExpect(status().isBadRequest());
    }

    @Test
    void topMerchantsTrendsComparisonAndStatistics() throws Exception {
        JsonNode merchants = get("/v1/analytics/merchants/top?startDate=" + lastMonth.atDay(1) + "&endDate="
                + lastMonth.atEndOfMonth());
        assertThat(merchants.get(0).get("merchantName").asText()).isEqualTo("Landlord");
        assertThat(merchants.get(0).get("percentage").decimalValue()).isEqualByComparingTo("69.6");

        JsonNode trends = get("/v1/analytics/trends?months=12");
        assertThat(trends.get("months")).hasSize(12);
        JsonNode last = trends.get("months").get(10);
        assertThat(last.get("expense").decimalValue()).isEqualByComparingTo("35900");
        assertThat(last.get("lastYearExpense").decimalValue()).isEqualByComparingTo("20000");
        assertThat(last.get("expenseChangeYearOverYear").decimalValue()).isEqualByComparingTo("79.5");
        assertThat(trends.at("/highestSpendingMonth/expense").decimalValue()).isEqualByComparingTo("35900");

        YearMonth twoAgo = thisMonth.minusMonths(2);
        JsonNode compare = get("/v1/analytics/compare?month1=" + twoAgo.getMonthValue() + "&year1=" + twoAgo.getYear()
                + "&month2=" + lastMonth.getMonthValue() + "&year2=" + lastMonth.getYear());
        assertThat(compare.at("/first/expense").decimalValue()).isEqualByComparingTo("7500");
        assertThat(compare.at("/second/expense").decimalValue()).isEqualByComparingTo("35900");
        assertThat(compare.get("expenseChange").decimalValue()).isEqualByComparingTo("28400");

        JsonNode stats = get("/v1/categories/" + testData.categoryId("Food") + "/statistics?startDate="
                + thisMonth.minusMonths(5).atDay(1) + "&endDate=" + thisMonth.atEndOfMonth());
        assertThat(stats.get("totalAmount").decimalValue()).isEqualByComparingTo("45100");
        assertThat(stats.get("transactionCount").asInt()).isEqualTo(6);
        assertThat(stats.at("/topMerchants/0/name").asText()).isEqualTo("Zomato");
    }

    @Test
    void exportsCsvAndPdf() throws Exception {
        String query = "month=" + lastMonth.getMonthValue() + "&year=" + lastMonth.getYear();
        String csv = mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .get("/v1/analytics/monthly/export?format=csv&" + query).header("Authorization", session.bearer()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(csv).contains("Spending by category").contains("Bills,25000.00");

        byte[] pdf = mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .get("/v1/analytics/monthly/export?format=pdf&" + query).header("Authorization", session.bearer()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsByteArray();
        assertThat(new String(pdf, 0, 5)).isEqualTo("%PDF-");

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .get("/v1/analytics/monthly/export?format=xlsx").header("Authorization", session.bearer()))
                .andExpect(status().isBadRequest());
    }

    @Test
    void forecastUsesWeightedLastThreeMonths() {
        assertThat(AnalyticsService.forecast(List.of(new BigDecimal("100")))).isEqualByComparingTo("100");
        assertThat(AnalyticsService.forecast(List.of(new BigDecimal("0"), new BigDecimal("300"), new BigDecimal("600"))))
                .isEqualByComparingTo("400"); // (600*3 + 300*2 + 0) / 6
        assertThat(AnalyticsService.forecast(List.of())).isEqualByComparingTo("0");
    }
}

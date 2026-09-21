package com.spendos.reports;

import static com.spendos.support.TestAuth.json;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.spendos.support.IntegrationTestBase;
import com.spendos.support.TestAuth;
import com.spendos.support.TestAuth.Session;
import com.spendos.support.TestData;
import com.spendos.transactions.domain.Account;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.YearMonth;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

class PredictionAndPlanningIntegrationTest extends IntegrationTestBase {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private TestData testData;

    private Session session;
    private final YearMonth thisMonth = YearMonth.now();

    @BeforeEach
    void setUp() throws Exception {
        session = TestAuth.newSession(mockMvc);
        Account account = testData.account(session.userId());
        // Three complete months: income 75,000, spending 30,000 (food 6,000) -> saving 45,000 a month
        for (int m = 1; m <= 3; m++) {
            YearMonth month = thisMonth.minusMonths(m);
            testData.credit(session.userId(), account.getId(), "Acme Corp", "Income", "75000", month.atDay(1));
            testData.debit(session.userId(), account.getId(), "Landlord", "Bills", "24000", month.atDay(2));
            testData.debit(session.userId(), account.getId(), "Zomato", "Food", "6000", month.atDay(10));
        }
        testData.credit(session.userId(), account.getId(), "Acme Corp", "Income", "75000", thisMonth.atDay(1));
        testData.debit(session.userId(), account.getId(), "Zomato", "Food", "3000", LocalDate.now());
    }

    private JsonNode data(ResultActions actions) throws Exception {
        return TestAuth.read(actions.andExpect(status().is2xxSuccessful()).andReturn().getResponse()
                .getContentAsString(StandardCharsets.UTF_8)).get("data");
    }

    private JsonNode get(Session who, String url) throws Exception {
        return data(mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get(url)
                .header("Authorization", who.bearer())));
    }

    private JsonNode post(String url, String body) throws Exception {
        return data(mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post(url)
                .header("Authorization", session.bearer()).contentType(MediaType.APPLICATION_JSON).content(body)));
    }

    @Test
    void predictionIsConsistentAndExplainsItsConfidence() throws Exception {
        JsonNode prediction = get(session, "/v1/reports/spending-prediction");

        BigDecimal spent = prediction.get("currentSpending").decimalValue();
        assertThat(spent).isEqualByComparingTo("3000");
        JsonNode range = prediction.get("projectedMonthEnd");
        assertThat(range.get("median").decimalValue()).isGreaterThanOrEqualTo(spent);
        assertThat(range.get("min").decimalValue()).isLessThanOrEqualTo(range.get("median").decimalValue());
        assertThat(range.get("max").decimalValue()).isGreaterThanOrEqualTo(range.get("median").decimalValue());
        assertThat(prediction.get("confidence").decimalValue()).isBetween(new BigDecimal("0.75"), new BigDecimal("0.95"));
        assertThat(prediction.get("confidenceReason").asText()).startsWith("Based on 3 months of history");
        assertThat(prediction.get("monthlyAverage").decimalValue()).isEqualByComparingTo("30000");
        assertThat(prediction.get("methodology").asText()).contains("extrapolated");

        YearMonth last = thisMonth.minusMonths(1);
        JsonNode past = get(session, "/v1/reports/spending-prediction?month=" + last.getMonthValue() + "&year=" + last.getYear());
        assertThat(past.at("/projectedMonthEnd/median").decimalValue()).isEqualByComparingTo("30000");
        assertThat(past.get("confidence").decimalValue()).isEqualByComparingTo("1");

        Session fresh = TestAuth.newSession(mockMvc);
        assertThat(get(fresh, "/v1/reports/spending-prediction").get("confidence").decimalValue())
                .isLessThan(new BigDecimal("0.55"));
    }

    @Test
    void affordabilityWeighsIncomeSpendingAndGoals() throws Exception {
        JsonNode laptop = post("/v1/reports/affordability", json("purchaseAmount", 10000, "purchaseDescription", "Laptop"));
        assertThat(laptop.at("/affordability/isAffordable").asBoolean()).isTrue();
        assertThat(laptop.at("/analysis/expectedIncome").decimalValue()).isEqualByComparingTo("75000");
        assertThat(laptop.at("/analysis/savingsImpact").decimalValue()).isEqualByComparingTo("-10000");
        assertThat(laptop.get("explanation").asText()).contains("the Laptop is likely affordable");
        assertThat(laptop.get("disclaimer").asText()).isEqualTo("This is a planning estimate, not professional financial advice.");

        JsonNode car = post("/v1/reports/affordability", json("purchaseAmount", 500000, "purchaseDescription", "Car"));
        assertThat(car.at("/affordability/isAffordable").asBoolean()).isFalse();
        assertThat(car.get("explanation").asText()).contains("more than you expect to earn");

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/v1/reports/affordability")
                        .header("Authorization", session.bearer()).contentType(MediaType.APPLICATION_JSON)
                        .content(json("purchaseAmount", -5)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void goalsProjectCompletionFromActualSavings() throws Exception {
        JsonNode goal = post("/v1/goals", json("goalName", "Emergency Fund", "goalType", "savings",
                "targetAmount", 150000, "currentProgress", 45000, "targetDate", LocalDate.now().plusMonths(10).toString()));
        assertThat(goal.get("progressPercentage").decimalValue()).isEqualByComparingTo("30.0");
        assertThat(goal.get("monthsToTarget").asInt()).isEqualTo(3); // 105,000 / 45,000 -> 3
        assertThat(goal.get("onTrack").asBoolean()).isTrue();

        JsonNode updated = post("/v1/goals/" + goal.get("id").asText() + "/contributions", json("amount", 5000));
        assertThat(updated.get("currentProgress").decimalValue()).isEqualByComparingTo("50000");

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/v1/goals")
                        .header("Authorization", session.bearer()).contentType(MediaType.APPLICATION_JSON)
                        .content(json("goalName", "Past", "targetAmount", 100, "targetDate", "2020-01-01")))
                .andExpect(status().isBadRequest());

        Session other = TestAuth.newSession(mockMvc);
        mockMvc.perform(delete("/v1/goals/" + goal.get("id").asText()).header("Authorization", other.bearer()))
                .andExpect(status().isNotFound());
        assertThat(get(session, "/v1/goals")).hasSize(1);
    }

    @Test
    void simulationsAreSavedComparedAndPrivate() throws Exception {
        post("/v1/goals", json("goalName", "Vacation", "targetAmount", 100000, "targetDate",
                LocalDate.now().plusYears(1).toString()));
        String food = testData.categoryId("Food").toString();

        JsonNode cut = post("/v1/simulations", "{\"simulationName\":\"Save 2000 on food\",\"scenarios\":[{\"type\":"
                + "\"spend_reduction\",\"categoryId\":\"" + food + "\",\"amount\":2000,\"period\":\"monthly\"}]}");
        assertThat(cut.at("/results/monthlyImpact/before").decimalValue()).isEqualByComparingTo("30000");
        assertThat(cut.at("/results/monthlyImpact/after").decimalValue()).isEqualByComparingTo("28000");
        assertThat(cut.at("/results/goalImpact/0/goalName").asText()).isEqualTo("Vacation");
        assertThat(cut.at("/results/goalImpact/0/currentMonthsToCompletion").asInt()).isEqualTo(3);

        JsonNode laptop = post("/v1/simulations", "{\"simulationName\":\"Buy laptop\",\"scenarios\":[{\"type\":"
                + "\"one_time_purchase\",\"amount\":70000}]}");
        assertThat(laptop.at("/results/goalImpact/0/projectedMonthsWithSimulation").asInt()).isEqualTo(4);

        JsonNode comparison = get(session, "/v1/simulations/compare?ids=" + cut.get("id").asText() + ","
                + laptop.get("id").asText());
        assertThat(comparison.get("simulations")).hasSize(2);
        assertThat(comparison.get("bestForSavingsId").asText()).isEqualTo(cut.get("id").asText());
        assertThat(get(session, "/v1/simulations")).hasSize(2);

        Session other = TestAuth.newSession(mockMvc);
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .get("/v1/simulations/" + cut.get("id").asText()).header("Authorization", other.bearer()))
                .andExpect(status().isNotFound());
        mockMvc.perform(delete("/v1/simulations/" + cut.get("id").asText()).header("Authorization", session.bearer()))
                .andExpect(status().isNoContent());
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/v1/simulations")
                        .header("Authorization", session.bearer()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"simulationName\":\"Bad\",\"scenarios\":[{\"type\":\"lottery\",\"amount\":1}]}"))
                .andExpect(status().isBadRequest());
    }
}

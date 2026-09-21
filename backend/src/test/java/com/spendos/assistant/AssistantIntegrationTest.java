package com.spendos.assistant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.spendos.support.IntegrationTestBase;
import com.spendos.support.TestAuth;
import com.spendos.support.TestAuth.Session;
import com.spendos.support.TestData;
import com.spendos.transactions.domain.Account;
import java.nio.charset.StandardCharsets;
import java.time.YearMonth;
import java.time.format.TextStyle;
import java.util.Locale;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Two complete months of data. Earlier: Bills 24,000, Food 6,200, Transport 4,800 (35,000).
 * Later: Bills 24,000, Food 8,400, Transport 3,600, Shopping 8,500 (44,500). Income 75,000 each.
 */
class AssistantIntegrationTest extends IntegrationTestBase {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private TestData testData;

    @Autowired
    private ObjectMapper objectMapper;

    private Session session;
    private final YearMonth later = YearMonth.now().minusMonths(1);
    private final YearMonth earlier = YearMonth.now().minusMonths(2);

    @BeforeEach
    void setUp() throws Exception {
        session = TestAuth.newSession(mockMvc);
        Account account = testData.account(session.userId());
        var user = session.userId();
        var id = account.getId();
        testData.credit(user, id, "Acme Corp", "Income", "75000", earlier.atDay(1));
        testData.debit(user, id, "Landlord", "Bills", "24000", earlier.atDay(2));
        testData.debit(user, id, "Zomato", "Food", "3000", earlier.atDay(5));
        testData.debit(user, id, "Swiggy", "Food", "3200", earlier.atDay(12));
        testData.debit(user, id, "Uber", "Transport", "4800", earlier.atDay(15));

        testData.credit(user, id, "Acme Corp", "Income", "75000", later.atDay(1));
        testData.debit(user, id, "Landlord", "Bills", "24000", later.atDay(2));
        testData.debit(user, id, "Zomato", "Food", "5000", later.atDay(6));
        testData.debit(user, id, "Zomato", "Food", "1200", later.atDay(9));
        testData.debit(user, id, "Swiggy", "Food", "2200", later.atDay(14));
        testData.debit(user, id, "Uber", "Transport", "3600", later.atDay(16));
        testData.debit(user, id, "Amazon", "Shopping", "8500", later.atDay(18));
    }

    private JsonNode ask(Session who, String question) throws Exception {
        String body = objectMapper.writeValueAsString(Map.of("question", question,
                "context", Map.of("selectedMonth", monthName(later), "selectedYear", later.getYear())));
        String response = mockMvc.perform(post("/v1/assistant/query").header("Authorization", who.bearer())
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        return TestAuth.read(response).get("data");
    }

    private static String monthName(YearMonth month) {
        return month.getMonth().getDisplayName(TextStyle.FULL, Locale.ENGLISH);
    }

    @Test
    void explainsWhySpendingRoseWithSupportingData() throws Exception {
        JsonNode answer = ask(session, "Why did I spend more this month?");

        assertThat(answer.get("intent").asText()).isEqualTo("spending_change");
        assertThat(answer.get("answerSource").asText()).isEqualTo("rules");
        assertThat(answer.get("answer").asText())
                .contains("You spent ₹44,500 in " + monthName(later))
                .contains("₹9,500 (+27.1%) more than ₹35,000 in " + monthName(earlier))
                .contains("Shopping (₹8,500 vs ₹0, new)")
                .contains("Food (₹8,400 vs ₹6,200, +35.5%)")
                .contains("partly offset by lower Transport spending (-₹1,200)");
        JsonNode food = answer.at("/supportingData/keyFindings/1");
        assertThat(food.get("category").asText()).isEqualTo("Food");
        assertThat(food.get("thisMonth").decimalValue()).isEqualByComparingTo("8400");
        assertThat(food.get("previousMonth").decimalValue()).isEqualByComparingTo("6200");
        assertThat(food.get("changePercentage").decimalValue()).isEqualByComparingTo("35.5");
        // Supporting transactions come from the biggest driver (Shopping).
        assertThat(answer.at("/supportingData/relatedTransactions/0/merchant").asText()).isEqualTo("Amazon");
        assertThat(answer.get("followUpQuestions")).isNotEmpty();

        assertThat(ask(session, "Why did I spend less this month?").get("answer").asText()).startsWith("Actually, you spent more.");
    }

    @Test
    void answersWhereTheMoneyWentAndCategoryAndMerchantTotals() throws Exception {
        JsonNode top = ask(session, "Where did most of my money go?");
        assertThat(top.get("intent").asText()).isEqualTo("top_categories");
        assertThat(top.at("/supportingData/keyFindings/0/category").asText()).isEqualTo("Bills");
        assertThat(top.at("/supportingData/keyFindings/0/percentage").decimalValue()).isEqualByComparingTo("53.9");
        assertThat(top.get("answer").asText()).contains("Bills (₹24,000, 53.9%), Shopping (₹8,500, 19.1%) and Food (₹8,400, 18.9%)");

        JsonNode food = ask(session, "How much did I spend on food?");
        assertThat(food.get("intent").asText()).isEqualTo("category_spend");
        assertThat(food.get("answer").asText())
                .contains("You spent ₹8,400 on Food in " + monthName(later) + " across 3 transactions.")
                .contains("35.5% more than ₹6,200")
                .contains("Zomato (₹6,200) and Swiggy (₹2,200)");

        JsonNode zomato = ask(session, "How much did I spend at Zomato?");
        assertThat(zomato.get("intent").asText()).isEqualTo("merchant_spend");
        assertThat(zomato.get("answer").asText()).contains("₹6,200 at Zomato").contains("across 2 transactions (₹3,100 on average)");
        assertThat(zomato.at("/supportingData/relatedTransactions")).hasSize(2);
    }

    @Test
    void listsLargestPurchasesComparesMonthsAndSummarises() throws Exception {
        JsonNode largest = ask(session, "What were my top 3 largest purchases?");
        assertThat(largest.get("intent").asText()).isEqualTo("largest_purchases");
        assertThat(largest.at("/supportingData/relatedTransactions")).hasSize(3);
        assertThat(largest.get("answer").asText()).startsWith("Your 3 largest purchases in " + monthName(later)
                + " were ₹24,000 at Landlord");

        JsonNode compare = ask(session, "Compare " + monthName(earlier) + " and " + monthName(later));
        assertThat(compare.get("intent").asText()).isEqualTo("compare_months");
        JsonNode total = compare.at("/supportingData/keyFindings/0");
        assertThat(total.get("category").asText()).isEqualTo("Total spending");
        assertThat(total.get("thisMonth").decimalValue()).isEqualByComparingTo("44500");
        assertThat(total.get("previousMonth").decimalValue()).isEqualByComparingTo("35000");
        assertThat(compare.get("answer").asText()).contains("net savings of +₹30,500 vs +₹40,000");

        JsonNode summary = ask(session, "How much did I save this month?");
        assertThat(summary.get("answer").asText()).contains("saving ₹30,500 (40.7% of your income)");
    }

    @Test
    void handlesUnknownFutureAndInvalidQuestions() throws Exception {
        JsonNode unknown = ask(session, "What's the weather tomorrow?");
        assertThat(unknown.get("intent").asText()).isEqualTo("unknown");
        assertThat(unknown.get("followUpQuestions")).hasSize(4);

        assertThat(ask(session, "How much did I spend in December 2099?").get("answer").asText())
                .contains("hasn't happened yet");

        for (String question : new String[] {"   ", "x".repeat(501)}) {
            mockMvc.perform(post("/v1/assistant/query").header("Authorization", session.bearer())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(Map.of("question", question))))
                    .andExpect(status().isBadRequest());
        }
        mockMvc.perform(post("/v1/assistant/query").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"question\":\"How much did I spend?\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void onlyAnswersFromTheAskingUsersData() throws Exception {
        Session other = TestAuth.newSession(mockMvc);
        JsonNode answer = ask(other, "How much did I spend at Zomato?");
        // Zomato is not one of this user's merchants, so it is not recognised and no data leaks.
        assertThat(answer.get("answer").asText()).doesNotContain("6,200").doesNotContain("44,500");
        assertThat(ask(other, "Where did most of my money go?").get("answer").asText())
                .startsWith("I couldn't find any spending");
    }

    @Test
    void suggestionsUseTheUsersOwnData() throws Exception {
        String response = mockMvc.perform(get("/v1/assistant/suggestions").header("Authorization", session.bearer()))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        JsonNode suggestions = TestAuth.read(response).get("data");
        assertThat(suggestions).hasSizeGreaterThanOrEqualTo(6);
        assertThat(suggestions.findValuesAsText("question")).contains("How much did I spend on Bills?");
    }
}

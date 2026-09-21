package com.spendos.merchants;

import static com.spendos.support.TestAuth.json;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.spendos.merchants.normalizer.MerchantNormalizer;
import com.spendos.support.IntegrationTestBase;
import com.spendos.support.TestAuth;
import com.spendos.support.TestAuth.Session;
import com.spendos.support.TestData;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

class MerchantIntegrationTest extends IntegrationTestBase {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private TestData testData;

    @Autowired
    private MerchantNormalizer normalizer;

    private Session session;
    private UUID accountId;

    @BeforeEach
    void setUp() throws Exception {
        session = TestAuth.newSession(mockMvc);
        accountId = testData.account(session.userId()).getId();
    }

    private JsonNode call(Session who, MockHttpServletRequestBuilder request) throws Exception {
        ResultActions result = mockMvc.perform(request.header("Authorization", who.bearer()));
        String body = result.andExpect(status().is2xxSuccessful()).andReturn().getResponse()
                .getContentAsString(StandardCharsets.UTF_8);
        return TestAuth.read(body).get("data");
    }

    private JsonNode addTransaction(String merchantName, String categoryId) throws Exception {
        String body = categoryId == null
                ? json("accountId", accountId, "merchantName", merchantName, "amount", 250, "transactionType", "debit",
                "transactionDate", LocalDate.now().minusDays(1).toString())
                : json("accountId", accountId, "merchantName", merchantName, "amount", 250, "transactionType", "debit",
                "transactionDate", LocalDate.now().minusDays(1).toString(), "categoryId", categoryId);
        return call(session, post("/v1/transactions").contentType(MediaType.APPLICATION_JSON).content(body));
    }

    @Test
    void normalizesStatementTextToCanonicalMerchants() {
        assertThat(normalizer.normalize("ZOMATO ONLINE").merchantName()).isEqualTo("Zomato");
        assertThat(normalizer.normalize("UPI-NETFLIX").merchantName()).isEqualTo("Netflix");

        MerchantNormalizer.Result typo = normalizer.normalize("SWIGYY");
        assertThat(typo.merchantName()).isEqualTo("Swiggy");
        assertThat(typo.matchType()).isEqualTo(MerchantNormalizer.SIMILAR);
        assertThat(typo.confidence()).isLessThan(normalizer.normalize("SWIGGY").confidence());

        MerchantNormalizer.Result unknown = normalizer.normalize("Sharma Tailors");
        assertThat(unknown.matched()).isFalse();
        assertThat(unknown.confidence()).isEqualByComparingTo("0.50");
    }

    @Test
    void manualEntriesUseTheNormalizerAndCategoryFollowsTheMerchant() throws Exception {
        JsonNode netflix = addTransaction("UPI-NETFLIX", null);
        assertThat(netflix.get("merchantName").asText()).isEqualTo("Netflix");
        assertThat(netflix.get("categoryName").asText()).isEqualTo("Subscriptions");
    }

    @Test
    void merchantCategoryCorrectionAppliesToPastAndFutureTransactionsForThisUserOnly() throws Exception {
        String food = testData.categoryId("Food").toString();
        String shopping = testData.categoryId("Shopping").toString();
        JsonNode first = addTransaction("Chaayos Koramangala", null);
        addTransaction("Chaayos Koramangala", shopping); // categorised by hand: must be left alone
        assertThat(first.get("categoryName").asText()).isEqualTo("Other");
        String merchantId = first.get("merchantId").asText();

        JsonNode listed = call(session, get("/v1/merchants?searchText=chaay"));
        assertThat(listed).hasSize(1);
        assertThat(listed.get(0).get("transactionCount").asLong()).isEqualTo(2);
        assertThat(listed.get(0).get("isVerified").asBoolean()).isFalse();

        JsonNode updated = call(session, put("/v1/merchants/" + merchantId + "/category")
                .contentType(MediaType.APPLICATION_JSON).content(json("categoryId", food)));
        assertThat(updated.get("categoryName").asText()).isEqualTo("Food");
        assertThat(updated.get("userCategory").asBoolean()).isTrue();

        JsonNode transactions = call(session, get("/v1/transactions?merchantId=" + merchantId));
        assertThat(transactions.findValuesAsText("categoryName")).containsExactlyInAnyOrder("Food", "Shopping");

        JsonNode later = addTransaction("chaayos koramangala", null);
        assertThat(later.get("categoryName").asText()).isEqualTo("Food");
        assertThat(later.get("categorizationSource").asText()).isEqualTo("merchant_mapping");

        // Other users cannot see this user's merchant, let alone its category.
        Session other = TestAuth.newSession(mockMvc);
        mockMvc.perform(get("/v1/merchants/" + merchantId).header("Authorization", other.bearer()))
                .andExpect(status().isNotFound());
        assertThat(call(other, get("/v1/merchants?searchText=chaay"))).isEmpty();
        assertThat(call(other, get("/v1/merchants?searchText=zomato"))).hasSize(1); // verified merchants are shared
    }

    @Test
    void suggestedCorrectionMapsRawNamesToKnownMerchants() throws Exception {
        String food = testData.categoryId("Food").toString();
        addTransaction("Swigy Instamart", null);

        JsonNode suggestions = call(session, get("/v1/merchants/suggestions"));
        assertThat(suggestions).hasSize(1);
        JsonNode suggestion = suggestions.get(0);
        assertThat(suggestion.get("merchantName").asText()).isEqualTo("Swigy Instamart");
        assertThat(suggestion.get("suggestedMerchantName").asText()).isEqualTo("Swiggy");
        assertThat(suggestion.get("similarity").decimalValue()).isEqualByComparingTo("0.83");

        JsonNode mapping = call(session, post("/v1/merchants/mappings").contentType(MediaType.APPLICATION_JSON)
                .content(json("rawMerchantName", "Swigy Instamart",
                        "normalizedMerchantId", suggestion.get("suggestedMerchantId").asText(), "categoryId", food)));
        assertThat(mapping.get("normalizedMerchantName").asText()).isEqualTo("Swiggy");
        assertThat(mapping.get("categoryName").asText()).isEqualTo("Food");
        assertThat(mapping.get("appliedToTransactions").asInt()).isEqualTo(1);

        // Future entries with the raw name land on Swiggy, and the suggestion is gone.
        JsonNode later = addTransaction("SWIGY INSTAMART", null);
        assertThat(later.get("merchantName").asText()).isEqualTo("Swiggy");
        assertThat(later.get("categoryName").asText()).isEqualTo("Food");
        assertThat(call(session, get("/v1/merchants/suggestions"))).isEmpty();

        JsonNode mappings = call(session, get("/v1/merchants/mappings"));
        assertThat(mappings).hasSize(1);
        assertThat(mappings.get(0).get("usageCount").asLong()).isEqualTo(2);

        String mappingId = mapping.get("id").asText();
        Session other = TestAuth.newSession(mockMvc);
        mockMvc.perform(delete("/v1/merchants/mappings/" + mappingId).header("Authorization", other.bearer()))
                .andExpect(status().isNotFound());
        mockMvc.perform(delete("/v1/merchants/mappings/" + mappingId).header("Authorization", session.bearer()))
                .andExpect(status().isNoContent());
        assertThat(call(session, get("/v1/merchants/mappings"))).isEmpty();
    }

    @Test
    void mappingsCanOnlyTargetMerchantsTheUserCanSee() throws Exception {
        Session other = TestAuth.newSession(mockMvc);
        UUID otherAccount = testData.account(other.userId()).getId();
        UUID hidden = testData.debit(other.userId(), otherAccount, "Secret Clinic Pvt", "Healthcare", "900",
                LocalDate.now().minusDays(2)).getMerchantId();

        mockMvc.perform(post("/v1/merchants/mappings").header("Authorization", session.bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json("rawMerchantName", "anything", "normalizedMerchantId", hidden.toString())))
                .andExpect(status().isNotFound());
        mockMvc.perform(post("/v1/merchants/mappings").header("Authorization", session.bearer())
                        .contentType(MediaType.APPLICATION_JSON).content(json("rawMerchantName", "")))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/v1/merchants?sortBy=bogus").header("Authorization", session.bearer()))
                .andExpect(status().isBadRequest());
    }
}

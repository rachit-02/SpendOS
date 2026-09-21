package com.spendos.transactions;

import static com.spendos.support.TestAuth.json;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.spendos.support.IntegrationTestBase;
import com.spendos.support.TestAuth;
import com.spendos.support.TestAuth.Session;
import com.spendos.support.TestData;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

class AccountAndCategoryIntegrationTest extends IntegrationTestBase {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private TestData testData;

    private String createAccount(Session session, String name, boolean primary) throws Exception {
        String body = mockMvc.perform(post("/v1/accounts").header("Authorization", session.bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json("accountName", name, "accountType", "savings", "accountNumberLast4", "1234",
                                "bankName", "HDFC", "isPrimary", primary)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return TestAuth.read(body).get("data").get("id").asText();
    }

    @Test
    void firstAccountIsPrimaryAndPrimaryMovesWhenRequested() throws Exception {
        Session session = TestAuth.newSession(mockMvc);
        String first = createAccount(session, "Salary account", false);
        String second = createAccount(session, "Wallet", true);

        mockMvc.perform(get("/v1/accounts").header("Authorization", session.bearer()))
                .andExpect(jsonPath("$.data", hasSize(2)))
                .andExpect(jsonPath("$.data[0].id").value(second))
                .andExpect(jsonPath("$.data[0].isPrimary").value(true))
                .andExpect(jsonPath("$.data[0].accountNumberMasked").value("XXXX1234"))
                .andExpect(jsonPath("$.data[1].isPrimary").value(false));

        mockMvc.perform(put("/v1/accounts/" + first).header("Authorization", session.bearer())
                        .contentType(MediaType.APPLICATION_JSON).content(json("isPrimary", true)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.isPrimary").value(true));
    }

    @Test
    void accountValidationAndOwnership() throws Exception {
        Session session = TestAuth.newSession(mockMvc);
        mockMvc.perform(post("/v1/accounts").header("Authorization", session.bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json("accountName", "X", "accountType", "crypto")))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post("/v1/accounts").header("Authorization", session.bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json("accountName", "X", "accountType", "savings", "accountNumberLast4", "123456789")))
                .andExpect(status().isBadRequest());

        String id = createAccount(session, "Mine", false);
        Session other = TestAuth.newSession(mockMvc);
        mockMvc.perform(put("/v1/accounts/" + id).header("Authorization", other.bearer())
                        .contentType(MediaType.APPLICATION_JSON).content(json("accountName", "Stolen")))
                .andExpect(status().isNotFound());
    }

    @Test
    void categoriesIncludeSeededSubcategories() throws Exception {
        Session session = TestAuth.newSession(mockMvc);
        mockMvc.perform(get("/v1/categories").header("Authorization", session.bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(greaterThanOrEqualTo(12))))
                .andExpect(jsonPath("$.data[0].categoryName").value("Food"))
                .andExpect(jsonPath("$.data[0].colorHex").value("#FF6B6B"))
                .andExpect(jsonPath("$.data[0].subcategories[1].subcategoryName").value("Food Delivery"));
    }

    @Test
    void databaseEnforcesPositiveAmountAndValidType() {
        UUID userId = jdbc.queryForObject(
                "INSERT INTO users (email, password_hash) VALUES (?, 'x') RETURNING id", UUID.class,
                TestAuth.uniqueEmail());
        UUID accountId = testData.account(userId).getId();

        assertThatThrownBy(() -> jdbc.update(
                "INSERT INTO transactions (user_id, account_id, amount, transaction_type, transaction_date) "
                        + "VALUES (?, ?, -5, 'debit', CURRENT_DATE)", userId, accountId))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update(
                "INSERT INTO transactions (user_id, account_id, amount, transaction_type, transaction_date) "
                        + "VALUES (?, ?, 5, 'gift', CURRENT_DATE)", userId, accountId))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update(
                "INSERT INTO transactions (user_id, account_id, amount, transaction_type, transaction_date) "
                        + "VALUES (?, ?, 5, 'debit', CURRENT_DATE + 30)", userId, accountId))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThat(jdbc.update(
                "INSERT INTO transactions (user_id, account_id, amount, transaction_type, transaction_date) "
                        + "VALUES (?, ?, 5, 'debit', CURRENT_DATE)", userId, accountId)).isEqualTo(1);
    }

    @Test
    void performanceIndexesExist() {
        var indexes = jdbc.queryForList(
                "SELECT indexname FROM pg_indexes WHERE tablename = 'transactions'", String.class);
        assertThat(indexes).contains("idx_transactions_user_date", "idx_transactions_user_merchant",
                "idx_transactions_user_category");
    }
}

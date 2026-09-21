package com.spendos.transactions;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.spendos.audit.repository.AuditLogRepository;
import com.spendos.support.IntegrationTestBase;
import com.spendos.support.TestAuth;
import com.spendos.support.TestAuth.Session;
import com.spendos.support.TestData;
import com.spendos.transactions.domain.Account;
import com.spendos.transactions.domain.Transaction;
import java.time.LocalDate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

class TransactionSearchIntegrationTest extends IntegrationTestBase {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private TestData testData;

    @Autowired
    private AuditLogRepository auditLogRepository;

    @Autowired
    private JdbcTemplate jdbc;

    private Session session;
    private Account account;

    @BeforeEach
    void setUp() throws Exception {
        session = TestAuth.newSession(mockMvc);
        account = testData.account(session.userId());
        LocalDate today = LocalDate.now();
        testData.debit(session.userId(), account.getId(), "Zomato", "Food", "450", today.minusDays(1));
        testData.debit(session.userId(), account.getId(), "Zomato", "Food", "320", today.minusDays(2));
        testData.debit(session.userId(), account.getId(), "Zepto", "Food", "900", today.minusDays(3));
        testData.debit(session.userId(), account.getId(), "Uber", "Transport", "250", today.minusDays(4));
    }

    @Test
    void exportsFilteredTransactionsAsCsvAndAuditsTheExport() throws Exception {
        String csv = mockMvc.perform(get("/v1/transactions/export?categoryId=" + testData.categoryId("Food"))
                        .header("Authorization", session.bearer()))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", containsString("attachment; filename=\"spendos-transactions-")))
                .andExpect(content().contentTypeCompatibleWith("text/csv"))
                .andReturn().getResponse().getContentAsString();

        assertThat(csv.lines()).hasSize(4);
        assertThat(csv.lines().findFirst().orElseThrow()).startsWith("Date,Type,Amount,Currency,Merchant,Category");
        assertThat(csv).contains("Zomato").contains("Zepto").doesNotContain("Uber");
        assertThat(auditLogRepository.findByUserIdOrderByCreatedAtDesc(session.userId()))
                .anySatisfy(entry -> assertThat(entry.getAction()).isEqualTo("export"));
    }

    @Test
    void exportOfSelectedIdsIgnoresOtherUsersTransactions() throws Exception {
        Session other = TestAuth.newSession(mockMvc);
        Account otherAccount = testData.account(other.userId());
        Transaction foreign = testData.debit(other.userId(), otherAccount.getId(), "Secret Shop", "Shopping", "999",
                LocalDate.now().minusDays(1));
        Transaction mine = testData.debit(session.userId(), account.getId(), "Chai Point", "Food", "40",
                LocalDate.now().minusDays(1));

        String csv = mockMvc.perform(get("/v1/transactions/export?ids=" + mine.getId() + "," + foreign.getId())
                        .header("Authorization", session.bearer()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(csv).contains("Chai Point").doesNotContain("Secret Shop");
    }

    @Test
    void formulaLikeCellsAreNeutralizedInExports() throws Exception {
        testData.debit(session.userId(), account.getId(), "=HYPERLINK(\"http://evil\")", "Other", "1",
                LocalDate.now().minusDays(1));

        String csv = mockMvc.perform(get("/v1/transactions/export").header("Authorization", session.bearer()))
                .andReturn().getResponse().getContentAsString();

        assertThat(csv).contains("'=HYPERLINK").doesNotContain(",=HYPERLINK");
    }

    @Test
    void suggestionsRankTheUsersMerchantsByUse() throws Exception {
        mockMvc.perform(get("/v1/transactions/suggestions?q=z").header("Authorization", session.bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.merchants[0].merchantName").value("Zomato"))
                .andExpect(jsonPath("$.data.merchants[0].count").value(2))
                .andExpect(jsonPath("$.data.merchants[1].merchantName").value("Zepto"))
                .andExpect(jsonPath("$.data.merchants.length()").value(2));

        mockMvc.perform(get("/v1/transactions/suggestions").header("Authorization", session.bearer()))
                .andExpect(jsonPath("$.data.categories[0].categoryName").value("Food"))
                .andExpect(jsonPath("$.data.categories[0].count").value(3));

        Session stranger = TestAuth.newSession(mockMvc);
        mockMvc.perform(get("/v1/transactions/suggestions?q=zomato").header("Authorization", stranger.bearer()))
                .andExpect(jsonPath("$.data.merchants.length()").value(0));
    }

    @Test
    void likeWildcardsInSearchAreMatchedLiterally() throws Exception {
        mockMvc.perform(get("/v1/transactions?searchText=%25").header("Authorization", session.bearer()))
                .andExpect(jsonPath("$.data.length()").value(0));
        mockMvc.perform(get("/v1/transactions?searchText=_").header("Authorization", session.bearer()))
                .andExpect(jsonPath("$.data.length()").value(0));
    }

    @Test
    void trigramIndexesSupportSubstringSearch() {
        assertThat(jdbc.queryForList("SELECT indexname FROM pg_indexes WHERE tablename = 'transactions'", String.class))
                .contains("idx_transactions_description_trgm", "idx_transactions_raw_description_trgm");
    }
}

package com.spendos.transactions;

import static com.spendos.support.TestAuth.json;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.spendos.audit.repository.AuditLogRepository;
import com.spendos.support.IntegrationTestBase;
import com.spendos.support.TestAuth;
import com.spendos.support.TestAuth.Session;
import com.spendos.support.TestData;
import com.spendos.transactions.domain.Account;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

class TransactionIntegrationTest extends IntegrationTestBase {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private TestData testData;

    @Autowired
    private AuditLogRepository auditLogRepository;

    private Session session;
    private Account account;

    @BeforeEach
    void setUp() throws Exception {
        session = TestAuth.newSession(mockMvc);
        account = testData.account(session.userId());
    }

    private ResultActions createTransaction(Session who, UUID accountId, Object amount, String date) throws Exception {
        return mockMvc.perform(post("/v1/transactions").header("Authorization", who.bearer())
                .contentType(MediaType.APPLICATION_JSON)
                .content(json("accountId", accountId, "merchantName", "  Zomato ", "categoryId", testData.categoryId("Food"),
                        "amount", amount, "transactionType", "debit", "transactionDate", date,
                        "description", "Lunch delivery", "paymentMethod", "upi")));
    }

    private String createdId(ResultActions actions) throws Exception {
        return TestAuth.read(actions.andReturn().getResponse().getContentAsString()).get("data").get("id").asText();
    }

    @Test
    void createReturns201WithResolvedNamesAndWritesAuditLog() throws Exception {
        ResultActions result = createTransaction(session, account.getId(), 450.00, "2026-09-05")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.id").isNotEmpty())
                .andExpect(jsonPath("$.data.merchantName").value("Zomato"))
                .andExpect(jsonPath("$.data.categoryName").value("Food"))
                .andExpect(jsonPath("$.data.amount").value(450.0))
                .andExpect(jsonPath("$.data.currencyCode").value("INR"))
                .andExpect(jsonPath("$.data.categorizationSource").value("user"));
        String id = createdId(result);

        assertThat(auditLogRepository.findByEntityTypeAndEntityIdOrderByCreatedAtAsc("transaction", id))
                .singleElement()
                .satisfies(entry -> {
                    assertThat(entry.getAction()).isEqualTo("create");
                    assertThat(entry.getUserId()).isEqualTo(session.userId());
                });
    }

    @Test
    void invalidAmountsAndDatesAreRejected() throws Exception {
        createTransaction(session, account.getId(), -100, "2026-09-05")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.details.amount").value("Amount must be positive"));
        createTransaction(session, account.getId(), 0, "2026-09-05").andExpect(status().isBadRequest());
        createTransaction(session, account.getId(), 12.345, "2026-09-05").andExpect(status().isBadRequest());
        createTransaction(session, account.getId(), 100, LocalDate.now().plusDays(3).toString())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_TRANSACTION"));
        createTransaction(session, account.getId(), 100, "not-a-date").andExpect(status().isBadRequest());
    }

    @Test
    void cannotUseAnotherUsersAccount() throws Exception {
        Session other = TestAuth.newSession(mockMvc);
        createTransaction(other, account.getId(), 100, "2026-09-05").andExpect(status().isNotFound());
    }

    @Test
    void readUpdateDeleteLifecycle() throws Exception {
        String id = createdId(createTransaction(session, account.getId(), 450, "2026-09-05"));

        mockMvc.perform(get("/v1/transactions/" + id).header("Authorization", session.bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.description").value("Lunch delivery"))
                .andExpect(jsonPath("$.data.isDuplicate").value(false));

        mockMvc.perform(put("/v1/transactions/" + id).header("Authorization", session.bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json("categoryId", testData.categoryId("Shopping"), "description", "Updated description",
                                "merchantName", "Blue Tokai Coffee")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.categoryName").value("Shopping"))
                .andExpect(jsonPath("$.data.merchantName").value("Blue Tokai Coffee"))
                .andExpect(jsonPath("$.data.description").value("Updated description"))
                .andExpect(jsonPath("$.data.amount").value(450.0));

        mockMvc.perform(delete("/v1/transactions/" + id).header("Authorization", session.bearer()))
                .andExpect(status().isNoContent());
        mockMvc.perform(get("/v1/transactions/" + id).header("Authorization", session.bearer()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("NOT_FOUND"));
    }

    @Test
    void otherUsersTransactionsAreInvisible() throws Exception {
        String id = createdId(createTransaction(session, account.getId(), 450, "2026-09-05"));
        Session intruder = TestAuth.newSession(mockMvc);

        mockMvc.perform(get("/v1/transactions/" + id).header("Authorization", intruder.bearer()))
                .andExpect(status().isNotFound());
        mockMvc.perform(put("/v1/transactions/" + id).header("Authorization", intruder.bearer())
                        .contentType(MediaType.APPLICATION_JSON).content(json("description", "hacked")))
                .andExpect(status().isNotFound());
        mockMvc.perform(delete("/v1/transactions/" + id).header("Authorization", intruder.bearer()))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/v1/transactions").header("Authorization", intruder.bearer()))
                .andExpect(jsonPath("$.data", hasSize(0)));
        mockMvc.perform(post("/v1/transactions/bulk-update").header("Authorization", intruder.bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"transactionIds\":[\"" + id + "\"],\"updates\":{\"categoryId\":\""
                                + testData.categoryId("Travel") + "\"}}"))
                .andExpect(jsonPath("$.data.updated").value(0))
                .andExpect(jsonPath("$.data.failed").value(1));

        // The owner still sees the untouched transaction
        mockMvc.perform(get("/v1/transactions/" + id).header("Authorization", session.bearer()))
                .andExpect(jsonPath("$.data.categoryName").value("Food"));
    }

    @Test
    void paginationSplitsHundredTransactionsIntoFivePages() throws Exception {
        LocalDate start = LocalDate.of(2026, 1, 1);
        for (int i = 0; i < 100; i++) {
            testData.debit(session.userId(), account.getId(), "Merchant " + (i % 7), "Food", String.valueOf(10 + i),
                    start.plusDays(i));
        }

        mockMvc.perform(get("/v1/transactions?page=1&pageSize=20").header("Authorization", session.bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(20)))
                .andExpect(jsonPath("$.data[0].transactionDate").value(start.plusDays(99).toString()))
                .andExpect(jsonPath("$.pagination.totalItems").value(100))
                .andExpect(jsonPath("$.pagination.totalPages").value(5))
                .andExpect(jsonPath("$.pagination.currentPage").value(1))
                .andExpect(jsonPath("$.pagination.hasNext").value(true))
                .andExpect(jsonPath("$.pagination.hasPrevious").value(false));

        mockMvc.perform(get("/v1/transactions?page=5&pageSize=20").header("Authorization", session.bearer()))
                .andExpect(jsonPath("$.data", hasSize(20)))
                .andExpect(jsonPath("$.pagination.hasNext").value(false));

        mockMvc.perform(get("/v1/transactions?pageSize=101").header("Authorization", session.bearer()))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/v1/transactions?page=0").header("Authorization", session.bearer()))
                .andExpect(status().isBadRequest());
    }

    @Test
    void filtersAndSortingWork() throws Exception {
        testData.debit(session.userId(), account.getId(), "Uber", "Transport", "250", LocalDate.of(2026, 8, 3));
        testData.debit(session.userId(), account.getId(), "Zomato", "Food", "450", LocalDate.of(2026, 8, 10));
        testData.debit(session.userId(), account.getId(), "Amazon", "Shopping", "8500", LocalDate.of(2026, 9, 2));
        testData.credit(session.userId(), account.getId(), "Acme Corp", "Income", "75000", LocalDate.of(2026, 9, 1));

        mockMvc.perform(get("/v1/transactions?startDate=2026-08-01&endDate=2026-08-31")
                        .header("Authorization", session.bearer()))
                .andExpect(jsonPath("$.data", hasSize(2)));
        mockMvc.perform(get("/v1/transactions?categoryId=" + testData.categoryId("Food"))
                        .header("Authorization", session.bearer()))
                .andExpect(jsonPath("$.data", hasSize(1)))
                .andExpect(jsonPath("$.data[0].merchantName").value("Zomato"));
        mockMvc.perform(get("/v1/transactions?transactionType=credit").header("Authorization", session.bearer()))
                .andExpect(jsonPath("$.data", hasSize(1)));
        mockMvc.perform(get("/v1/transactions?minAmount=300&maxAmount=9000&sortBy=amount&sortOrder=asc")
                        .header("Authorization", session.bearer()))
                .andExpect(jsonPath("$.data", hasSize(2)))
                .andExpect(jsonPath("$.data[0].amount").value(450.0))
                .andExpect(jsonPath("$.data[1].amount").value(8500.0));
        mockMvc.perform(get("/v1/transactions?searchText=zoma").header("Authorization", session.bearer()))
                .andExpect(jsonPath("$.data", hasSize(1)));
        mockMvc.perform(get("/v1/transactions?sortBy=merchant&sortOrder=asc").header("Authorization", session.bearer()))
                .andExpect(jsonPath("$.data[0].merchantName").value("Acme Corp"));
        mockMvc.perform(get("/v1/transactions?startDate=2026-09-01&endDate=2026-08-01")
                        .header("Authorization", session.bearer()))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/v1/transactions?startDate=01-08-2026").header("Authorization", session.bearer()))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/v1/transactions?sortBy=password").header("Authorization", session.bearer()))
                .andExpect(status().isBadRequest());
    }

    @Test
    void bulkUpdateAndBulkDelete() throws Exception {
        String a = createdId(createTransaction(session, account.getId(), 100, "2026-09-01"));
        String b = createdId(createTransaction(session, account.getId(), 200, "2026-09-02"));
        UUID travel = testData.categoryId("Travel");

        mockMvc.perform(post("/v1/transactions/bulk-update").header("Authorization", session.bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"transactionIds\":[\"" + a + "\",\"" + b + "\",\"" + UUID.randomUUID()
                                + "\"],\"updates\":{\"categoryId\":\"" + travel + "\"}}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.updated").value(2))
                .andExpect(jsonPath("$.data.failed").value(1));
        mockMvc.perform(get("/v1/transactions?categoryId=" + travel).header("Authorization", session.bearer()))
                .andExpect(jsonPath("$.data", hasSize(2)));

        mockMvc.perform(post("/v1/transactions/bulk-delete").header("Authorization", session.bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"transactionIds\":[\"" + a + "\",\"" + b + "\"]}"))
                .andExpect(jsonPath("$.data.deleted").value(2));
        mockMvc.perform(get("/v1/transactions").header("Authorization", session.bearer()))
                .andExpect(jsonPath("$.data", hasSize(0)));
    }

    @Test
    void htmlInDescriptionIsStoredAndReturnedAsInertJsonText() throws Exception {
        String payload = "<script>alert('XSS')</script>";
        mockMvc.perform(post("/v1/transactions").header("Authorization", session.bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json("accountId", account.getId(), "merchantName", "Corner Shop " + UUID.randomUUID(),
                                "amount", 100, "transactionType", "debit", "transactionDate", "2026-09-01",
                                "description", payload)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.description").value(payload))
                .andExpect(jsonPath("$.data.categoryName").value("Other"));
    }
}

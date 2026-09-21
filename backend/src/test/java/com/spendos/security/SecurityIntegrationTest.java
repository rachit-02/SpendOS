package com.spendos.security;

import static com.spendos.support.TestAuth.json;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.spendos.support.IntegrationTestBase;
import com.spendos.support.TestAuth;
import com.spendos.support.TestAuth.Session;
import com.spendos.support.TestData;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

/** Phase 15 checks: every endpoint requires authentication, headers, injection and XSS handling. */
class SecurityIntegrationTest extends IntegrationTestBase {

    private static final List<String> PUBLIC_PREFIXES = List.of("/v1/auth/", "/v1/health");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    @Qualifier("requestMappingHandlerMapping")
    private RequestMappingHandlerMapping handlerMapping;

    @Autowired
    private TestData testData;

    @Autowired
    private JdbcTemplate jdbc;

    private record Endpoint(HttpMethod method, String path) {
    }

    /** Every /v1 endpoint in the application, with path variables filled with random ids. */
    private List<Endpoint> protectedEndpoints() {
        List<Endpoint> endpoints = new ArrayList<>();
        for (Map.Entry<RequestMappingInfo, HandlerMethod> entry : handlerMapping.getHandlerMethods().entrySet()) {
            RequestMappingInfo info = entry.getKey();
            for (String pattern : info.getPatternValues()) {
                if (!pattern.startsWith("/v1/") || PUBLIC_PREFIXES.stream().anyMatch(pattern::startsWith)
                        || pattern.equals("/v1/health")) {
                    continue;
                }
                String path = pattern.replaceAll("\\{[^}]+}", UUID.randomUUID().toString());
                for (RequestMethod method : info.getMethodsCondition().getMethods()) {
                    endpoints.add(new Endpoint(HttpMethod.valueOf(method.name()), path));
                }
            }
        }
        return endpoints;
    }

    @Test
    void everyNonPublicEndpointRejectsMissingAndForgedTokens() throws Exception {
        List<Endpoint> endpoints = protectedEndpoints();
        assertThat(endpoints).hasSizeGreaterThan(60);

        // alg=none token claiming to be a real user: must never be accepted.
        Session victim = TestAuth.newSession(mockMvc);
        String header = Base64.getUrlEncoder().withoutPadding().encodeToString("{\"alg\":\"none\",\"typ\":\"JWT\"}".getBytes());
        String claims = Base64.getUrlEncoder().withoutPadding().encodeToString(
                ("{\"sub\":\"" + victim.userId() + "\",\"iss\":\"spendos\",\"type\":\"access\",\"exp\":4102444800}").getBytes());
        String unsigned = header + "." + claims + ".";
        String tampered = victim.bearer().substring(0, victim.bearer().length() - 4) + "abcd";

        List<String> failures = new ArrayList<>();
        for (Endpoint endpoint : endpoints) {
            for (String authorization : new String[] {null, "Bearer " + unsigned, tampered, "Basic dXNlcjpwYXNz"}) {
                var request = request(endpoint.method(), endpoint.path()).contentType(MediaType.APPLICATION_JSON).content("{}");
                if (authorization != null) {
                    request.header("Authorization", authorization);
                }
                int status = mockMvc.perform(request).andReturn().getResponse().getStatus();
                if (status != 401) {
                    failures.add(endpoint.method() + " " + endpoint.path() + " -> " + status + " with " + authorization);
                }
            }
        }
        assertThat(failures).isEmpty();
    }

    @Test
    void securityHeadersArePresentOnApiResponses() throws Exception {
        mockMvc.perform(get("/v1/health").secure(true))
                .andExpect(header().string("Content-Security-Policy", "default-src 'none'; frame-ancestors 'none'; base-uri 'none'; form-action 'none'"))
                .andExpect(header().string("X-Frame-Options", "DENY"))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(header().string("Referrer-Policy", "no-referrer"))
                .andExpect(header().string("X-XSS-Protection", "1; mode=block"))
                .andExpect(header().string("Strict-Transport-Security", "max-age=31536000 ; includeSubDomains"))
                .andExpect(header().exists("Permissions-Policy"));
        // Error responses carry them too.
        mockMvc.perform(get("/v1/transactions"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string("X-Frame-Options", "DENY"))
                .andExpect(header().exists("Content-Security-Policy"));
    }

    @Test
    void injectionAndScriptPayloadsAreStoredAsPlainData() throws Exception {
        Session session = TestAuth.newSession(mockMvc);
        UUID account = testData.account(session.userId()).getId();
        Session other = TestAuth.newSession(mockMvc);
        testData.debit(other.userId(), testData.account(other.userId()).getId(), "Other User Shop", "Food", "999",
                LocalDate.now().minusDays(1));

        String script = "<script>alert(document.cookie)</script>";
        String sql = "x'; DROP TABLE transactions; --";
        String body = mockMvc.perform(post("/v1/transactions").header("Authorization", session.bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json("accountId", account, "merchantName", sql, "amount", 10, "transactionType", "debit",
                                "transactionDate", LocalDate.now().minusDays(1).toString(), "description", script)))
                .andExpect(status().isCreated())
                .andExpect(header().string("Content-Type", org.hamcrest.Matchers.startsWith("application/json")))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        JsonNode created = TestAuth.read(body).get("data");
        assertThat(created.get("description").asText()).isEqualTo(script); // stored verbatim, escaped by the UI
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM transactions", Long.class)).isPositive();

        for (String attack : new String[] {"' OR '1'='1", "%", "_", "\\", "' UNION SELECT password_hash FROM users --"}) {
            JsonNode found = TestAuth.read(mockMvc.perform(get("/v1/transactions").param("searchText", attack)
                            .header("Authorization", session.bearer()))
                    .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8));
            assertThat(found.toString()).doesNotContain("Other User Shop").doesNotContain("$2a$");
            JsonNode merchants = TestAuth.read(mockMvc.perform(get("/v1/merchants").param("searchText", attack)
                            .header("Authorization", session.bearer()))
                    .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8));
            assertThat(merchants.toString()).doesNotContain("Other User Shop");
        }
        // "%" is matched literally, not as "everything".
        JsonNode percent = TestAuth.read(mockMvc.perform(get("/v1/merchants").param("searchText", "%")
                .header("Authorization", session.bearer())).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8));
        assertThat(percent.get("data")).isEmpty();

        // Sort parameters are allow-listed rather than passed to SQL.
        mockMvc.perform(get("/v1/transactions").param("sortBy", "amount; DROP TABLE users")
                        .header("Authorization", session.bearer()))
                .andExpect(status().isBadRequest());
    }

    @Test
    void validationRejectsOversizedAndMalformedInput() throws Exception {
        Session session = TestAuth.newSession(mockMvc);
        UUID account = testData.account(session.userId()).getId();
        mockMvc.perform(post("/v1/transactions").header("Authorization", session.bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json("accountId", account, "merchantName", "x".repeat(300), "amount", 10,
                                "transactionType", "debit", "transactionDate", LocalDate.now().toString())))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post("/v1/transactions").header("Authorization", session.bearer())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"amount\": \"1e309\""))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/v1/transactions/not-a-uuid").header("Authorization", session.bearer()))
                .andExpect(status().isBadRequest());
    }
}

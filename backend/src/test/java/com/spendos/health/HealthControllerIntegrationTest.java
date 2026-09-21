package com.spendos.health;

import static org.hamcrest.Matchers.matchesPattern;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.spendos.support.IntegrationTestBase;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

class HealthControllerIntegrationTest extends IntegrationTestBase {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void healthEndpointIsPublicAndReportsDatabaseUp() throws Exception {
        mockMvc.perform(get("/v1/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.status").value("UP"))
                .andExpect(jsonPath("$.data.checks.database").value("UP"))
                .andExpect(jsonPath("$.requestId").isNotEmpty());
    }

    @Test
    void requestIdIsEchoedWhenClientSendsValidUuid() throws Exception {
        String requestId = "550e8400-e29b-41d4-a716-446655440000";
        mockMvc.perform(get("/v1/health").header("X-Request-ID", requestId))
                .andExpect(header().string("X-Request-ID", requestId))
                .andExpect(jsonPath("$.requestId").value(requestId));
    }

    @Test
    void invalidClientRequestIdIsReplaced() throws Exception {
        mockMvc.perform(get("/v1/health").header("X-Request-ID", "<script>"))
                .andExpect(header().string("X-Request-ID", matchesPattern("[0-9a-f-]{36}")));
    }

    @Test
    void protectedEndpointWithoutTokenReturnsJson401() throws Exception {
        mockMvc.perform(get("/v1/users/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"));
    }

    @Test
    void openApiDocumentIsServed() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.info.title").value("SpendOS API"));
    }

    @Test
    void flywayAppliedAllMigrationsAndSeededCategories() {
        Integer failed = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM flyway_schema_history WHERE success = false", Integer.class);
        Integer categories = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM categories", Integer.class);
        org.assertj.core.api.Assertions.assertThat(failed).isZero();
        org.assertj.core.api.Assertions.assertThat(categories).isGreaterThanOrEqualTo(12);
    }
}

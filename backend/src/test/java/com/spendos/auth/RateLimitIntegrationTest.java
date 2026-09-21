package com.spendos.auth;

import static com.spendos.support.TestAuth.json;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.spendos.common.security.RateLimiter;
import com.spendos.support.IntegrationTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

@TestPropertySource(properties = "app.rate-limit.enabled=true")
class RateLimitIntegrationTest extends IntegrationTestBase {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private RateLimiter rateLimiter;

    @BeforeEach
    void reset() {
        rateLimiter.reset();
    }

    @Test
    void sixthLoginAttemptWithinFifteenMinutesIsRejectedWith429() throws Exception {
        String body = json("email", "nobody@example.com", "password", "Whatever-Passw0rd!");
        for (int attempt = 1; attempt <= 5; attempt++) {
            mockMvc.perform(post("/v1/auth/login").contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isUnauthorized());
        }
        mockMvc.perform(post("/v1/auth/login").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"))
                .andExpect(jsonPath("$.error.code").value("RATE_LIMITED"));
    }

    @Test
    void registrationIsLimitedPerIp() throws Exception {
        for (int attempt = 1; attempt <= 5; attempt++) {
            mockMvc.perform(post("/v1/auth/register").contentType(MediaType.APPLICATION_JSON)
                            .content(json("email", "bad", "password", "x", "fullName", "x")))
                    .andExpect(status().isBadRequest());
        }
        mockMvc.perform(post("/v1/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content(json("email", "bad", "password", "x", "fullName", "x")))
                .andExpect(status().isTooManyRequests());
    }
}

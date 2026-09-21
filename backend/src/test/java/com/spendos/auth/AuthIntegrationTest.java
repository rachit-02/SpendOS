package com.spendos.auth;

import static com.spendos.support.TestAuth.json;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.spendos.support.IntegrationTestBase;
import com.spendos.support.TestAuth;
import com.spendos.support.TestAuth.Session;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

class AuthIntegrationTest extends IntegrationTestBase {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void registerReturns201WithUserAndNeverThePasswordHash() throws Exception {
        String email = TestAuth.uniqueEmail();
        mockMvc.perform(post("/v1/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content(json("email", email.toUpperCase(), "password", TestAuth.PASSWORD, "fullName", " Jane ")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.email").value(email))
                .andExpect(jsonPath("$.data.fullName").value("Jane"))
                .andExpect(jsonPath("$.data.userId").isNotEmpty())
                .andExpect(jsonPath("$.data.passwordHash").doesNotExist());
    }

    @Test
    void duplicateEmailIsRejected() throws Exception {
        Session session = TestAuth.newSession(mockMvc);
        mockMvc.perform(post("/v1/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content(json("email", session.email(), "password", TestAuth.PASSWORD, "fullName", "Dup")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("DUPLICATE_EMAIL"));
    }

    @Test
    void weakPasswordIsRejected() throws Exception {
        mockMvc.perform(post("/v1/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content(json("email", TestAuth.uniqueEmail(), "password", "Abc123", "fullName", "Weak")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("WEAK_PASSWORD"));
    }

    @Test
    void invalidEmailFormatIsRejected() throws Exception {
        mockMvc.perform(post("/v1/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content(json("email", "not-an-email", "password", TestAuth.PASSWORD, "fullName", "X")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_REQUEST"))
                .andExpect(jsonPath("$.error.details.email").exists());
    }

    @Test
    void loginReturnsTokensAndWrongPasswordReturns401() throws Exception {
        Session session = TestAuth.newSession(mockMvc);
        org.assertj.core.api.Assertions.assertThat(session.accessToken()).isNotBlank();

        mockMvc.perform(post("/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(json("email", session.email(), "password", "Wrong-Passw0rd!")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"))
                .andExpect(jsonPath("$.error.message").value("Invalid email or password"));

        mockMvc.perform(post("/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(json("email", "nobody@example.com", "password", TestAuth.PASSWORD)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.message").value("Invalid email or password"));
    }

    @Test
    void usersMeRequiresValidAccessToken() throws Exception {
        Session session = TestAuth.newSession(mockMvc);

        mockMvc.perform(get("/v1/users/me")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/v1/users/me").header("Authorization", "Bearer not-a-jwt"))
                .andExpect(status().isUnauthorized());
        // A refresh token must not be usable as an access token
        mockMvc.perform(get("/v1/users/me").header("Authorization", "Bearer " + session.refreshToken()))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/v1/users/me").header("Authorization", session.bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.email").value(session.email()))
                .andExpect(jsonPath("$.data.emailVerified").value(false));
    }

    @Test
    void refreshTokensAreSingleUse() throws Exception {
        Session session = TestAuth.newSession(mockMvc);

        String body = mockMvc.perform(post("/v1/auth/refresh").contentType(MediaType.APPLICATION_JSON)
                        .content(json("refreshToken", session.refreshToken())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.data.expiresIn").value(3600))
                .andReturn().getResponse().getContentAsString();
        String newAccess = TestAuth.read(body).get("data").get("accessToken").asText();

        mockMvc.perform(post("/v1/auth/refresh").contentType(MediaType.APPLICATION_JSON)
                        .content(json("refreshToken", session.refreshToken())))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/v1/users/me").header("Authorization", "Bearer " + newAccess))
                .andExpect(status().isOk());
    }

    @Test
    void logoutRevokesAccessAndRefreshTokens() throws Exception {
        Session session = TestAuth.newSession(mockMvc);

        mockMvc.perform(post("/v1/auth/logout").header("Authorization", session.bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json("refreshToken", session.refreshToken())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.message").value("Logged out successfully"));

        mockMvc.perform(get("/v1/users/me").header("Authorization", session.bearer()))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/v1/auth/refresh").contentType(MediaType.APPLICATION_JSON)
                        .content(json("refreshToken", session.refreshToken())))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void profileUpdateChangesNameAndRejectsTakenEmail() throws Exception {
        Session session = TestAuth.newSession(mockMvc);
        Session other = TestAuth.newSession(mockMvc);

        mockMvc.perform(put("/v1/users/me").header("Authorization", session.bearer())
                        .contentType(MediaType.APPLICATION_JSON).content(json("fullName", "Jane Doe")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.fullName").value("Jane Doe"));

        mockMvc.perform(put("/v1/users/me").header("Authorization", session.bearer())
                        .contentType(MediaType.APPLICATION_JSON).content(json("email", other.email())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("DUPLICATE_EMAIL"));
    }

    @Test
    void changePasswordInvalidatesExistingTokens() throws Exception {
        Session session = TestAuth.newSession(mockMvc);
        String newPassword = "C0ff33#M0rning$";

        mockMvc.perform(post("/v1/users/me/change-password").header("Authorization", session.bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json("currentPassword", "wrong", "newPassword", newPassword)))
                .andExpect(status().isUnauthorized());

        Thread.sleep(1100); // tokens carry second-precision iat; make the old token strictly older
        mockMvc.perform(post("/v1/users/me/change-password").header("Authorization", session.bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json("currentPassword", TestAuth.PASSWORD, "newPassword", newPassword)))
                .andExpect(status().isOk());

        mockMvc.perform(get("/v1/users/me").header("Authorization", session.bearer()))
                .andExpect(status().isUnauthorized());
        Session relogin = TestAuth.login(mockMvc, session.email(), newPassword);
        mockMvc.perform(get("/v1/users/me").header("Authorization", relogin.bearer()))
                .andExpect(status().isOk());
    }

    @Test
    void deleteAccountRequiresPasswordThenBlocksLogin() throws Exception {
        Session session = TestAuth.newSession(mockMvc);

        mockMvc.perform(delete("/v1/users/me").header("Authorization", session.bearer())
                        .contentType(MediaType.APPLICATION_JSON).content(json("confirmPassword", "wrong")))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(delete("/v1/users/me").header("Authorization", session.bearer())
                        .contentType(MediaType.APPLICATION_JSON).content(json("confirmPassword", TestAuth.PASSWORD)))
                .andExpect(status().isOk());

        mockMvc.perform(get("/v1/users/me").header("Authorization", session.bearer()))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(json("email", session.email(), "password", TestAuth.PASSWORD)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void preferencesHaveDefaultsAndCanBeUpdated() throws Exception {
        Session session = TestAuth.newSession(mockMvc);

        mockMvc.perform(get("/v1/users/me/preferences").header("Authorization", session.bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.currencyCode").value("INR"))
                .andExpect(jsonPath("$.data.timezone").value("Asia/Kolkata"))
                .andExpect(jsonPath("$.data.theme").value("light"));

        mockMvc.perform(put("/v1/users/me/preferences").header("Authorization", session.bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json("currencyCode", "USD", "timezone", "America/New_York", "theme", "dark",
                                "emailReportsEnabled", true)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.currencyCode").value("USD"))
                .andExpect(jsonPath("$.data.theme").value("dark"))
                .andExpect(jsonPath("$.data.language").value("en"))
                .andExpect(jsonPath("$.data.emailReportsEnabled").value(true));

        mockMvc.perform(put("/v1/users/me/preferences").header("Authorization", session.bearer())
                        .contentType(MediaType.APPLICATION_JSON).content(json("timezone", "Mars/Olympus")))
                .andExpect(status().isBadRequest());
    }
}

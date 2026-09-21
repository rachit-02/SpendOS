package com.spendos.support;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.UUID;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

/** Registers and logs in throwaway users for integration tests. */
public final class TestAuth {

    public static final String PASSWORD = "Tr0pic@lThund3r!";
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private TestAuth() {
    }

    public record Session(UUID userId, String email, String accessToken, String refreshToken) {
        public String bearer() {
            return "Bearer " + accessToken;
        }
    }

    public static String uniqueEmail() {
        return "user-" + UUID.randomUUID().toString().substring(0, 8) + "@example.com";
    }

    public static Session newSession(MockMvc mockMvc) throws Exception {
        return register(mockMvc, uniqueEmail());
    }

    public static Session register(MockMvc mockMvc, String email) throws Exception {
        mockMvc.perform(post("/v1/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content(json("email", email, "password", PASSWORD, "fullName", "Test User")))
                .andExpect(status().isCreated());
        return login(mockMvc, email, PASSWORD);
    }

    public static Session login(MockMvc mockMvc, String email, String password) throws Exception {
        String body = mockMvc.perform(post("/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(json("email", email, "password", password)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        JsonNode data = MAPPER.readTree(body).get("data");
        return new Session(UUID.fromString(data.get("user").get("userId").asText()), email,
                data.get("accessToken").asText(), data.get("refreshToken").asText());
    }

    /** Builds a flat JSON object from alternating key/value arguments. */
    public static String json(Object... keyValues) {
        var node = MAPPER.createObjectNode();
        for (int i = 0; i < keyValues.length; i += 2) {
            node.putPOJO((String) keyValues[i], keyValues[i + 1]);
        }
        try {
            return MAPPER.writeValueAsString(node);
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }

    public static JsonNode read(String body) throws Exception {
        return MAPPER.readTree(body);
    }
}

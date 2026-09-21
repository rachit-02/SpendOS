package com.spendos.assistant.llm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.spendos.assistant.dto.AssistantDtos.Finding;
import com.spendos.assistant.llm.ExplanationService.Explained;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class ExplanationTest {

    private static final String DRAFT = "You spent ₹8,400 on Food in September, 35.5% more than ₹6,200 in August.";
    private static final List<Finding> FINDINGS = List.of(Finding.comparison("Food", null, new BigDecimal("8400.00"),
            new BigDecimal("6200.00"), new BigDecimal("2200.00"), new BigDecimal("35.5")));

    private static ExplanationService service(ExplanationClient client) {
        StaticListableBeanFactory beans = new StaticListableBeanFactory(client == null ? Map.of() : Map.of("llm", client));
        return new ExplanationService(beans.getBeanProvider(ExplanationClient.class), new ObjectMapper());
    }

    @Test
    void verifierAcceptsOnlyNumbersTheBackendSupplied() {
        String facts = DRAFT + " 2200.00";
        assertThat(AnswerVerifier.usesOnlyKnownNumbers("Food rose by ₹2,200 (35.5%) to ₹8,400.", facts)).isTrue();
        assertThat(AnswerVerifier.usesOnlyKnownNumbers("Food spending grew noticeably.", facts)).isTrue();
        assertThat(AnswerVerifier.usesOnlyKnownNumbers("Food rose 36% to ₹8,400.", facts)).isTrue(); // rounding
        assertThat(AnswerVerifier.usesOnlyKnownNumbers("Food rose to ₹9,100.", facts)).isFalse();
        assertThat(AnswerVerifier.usesOnlyKnownNumbers("You could save ₹500 a week.", facts)).isFalse();
    }

    @Test
    void withoutAModelTheDeterministicAnswerIsReturned() {
        Explained explained = service(null).explain("Why?", DRAFT, FINDINGS);
        assertThat(explained.text()).isEqualTo(DRAFT);
        assertThat(explained.source()).isEqualTo(ExplanationService.RULES);
    }

    @Test
    void acceptsAVerifiedRewordingAndRejectsInventedNumbers() {
        Explained good = service((q, facts, draft) -> "Food is up 35.5%: ₹8,400 against ₹6,200 last month.")
                .explain("Why?", DRAFT, FINDINGS);
        assertThat(good.source()).isEqualTo(ExplanationService.LLM);
        assertThat(good.text()).startsWith("Food is up");

        Explained invented = service((q, facts, draft) -> "Food is up, mostly from 14 Swiggy orders averaging ₹310.")
                .explain("Why?", DRAFT, FINDINGS);
        assertThat(invented.source()).isEqualTo(ExplanationService.RULES);
        assertThat(invented.text()).isEqualTo(DRAFT);

        Explained failing = service((q, facts, draft) -> {
            throw new IllegalStateException("timeout");
        }).explain("Why?", DRAFT, FINDINGS);
        assertThat(failing.text()).isEqualTo(DRAFT);
    }

    @Test
    void anthropicClientSendsStructuredFactsAndReadsTheText() {
        RestClient.Builder builder = RestClient.builder().baseUrl("https://llm.test").defaultHeader("x-api-key", "test-key");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        AnthropicExplanationClient client = new AnthropicExplanationClient(builder.build(), "claude-haiku-4-5-20251001", 400);
        server.expect(requestTo("https://llm.test/v1/messages"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("x-api-key", "test-key"))
                .andExpect(jsonPath("$.model").value("claude-haiku-4-5-20251001"))
                .andExpect(jsonPath("$.system").value(org.hamcrest.Matchers.containsString("Never add")))
                .andExpect(jsonPath("$.messages[0].content").value(org.hamcrest.Matchers.containsString("<facts>[{\"category\":\"Food\"")))
                .andRespond(withSuccess("{\"content\":[{\"type\":\"text\",\"text\":\" Food is up 35.5%. \"}]}",
                        MediaType.APPLICATION_JSON));
        server.expect(requestTo("https://llm.test/v1/messages")).andRespond(withServerError());

        ExplanationService explanations = service(client);
        Explained explained = explanations.explain("Why did I spend more?", DRAFT, FINDINGS);
        assertThat(explained.text()).isEqualTo("Food is up 35.5%.");
        assertThat(explained.source()).isEqualTo(ExplanationService.LLM);

        // A provider error falls back to the deterministic answer.
        assertThat(explanations.explain("Why did I spend more?", DRAFT, FINDINGS).text()).isEqualTo(DRAFT);
        server.verify();
    }

    @Test
    void enablingTheModelWithoutAKeyFailsFast() {
        assertThatThrownBy(() -> new AnthropicExplanationClient(RestClient.builder(), "https://llm.test", "",
                "claude-haiku-4-5-20251001", 400, Duration.ofSeconds(1)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("ANTHROPIC_API_KEY");
    }
}

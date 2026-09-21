package com.spendos.assistant.llm;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.spendos.assistant.dto.AssistantDtos.Finding;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

/**
 * Optionally rewords a deterministic answer with a language model. The model is used for
 * explanation only: its text is accepted only if every number in it is one the backend supplied;
 * otherwise, or on any error or timeout, the deterministic answer is returned unchanged.
 */
@Service
public class ExplanationService {

    public record Explained(String text, String source) {
    }

    public static final String RULES = "rules";
    public static final String LLM = "llm";
    private static final int MAX_LENGTH = 1500;
    private static final Logger log = LoggerFactory.getLogger(ExplanationService.class);

    private final ObjectProvider<ExplanationClient> client;
    private final ObjectMapper objectMapper;

    public ExplanationService(ObjectProvider<ExplanationClient> client, ObjectMapper objectMapper) {
        this.client = client;
        this.objectMapper = objectMapper;
    }

    public Explained explain(String question, String draft, List<Finding> findings) {
        ExplanationClient llm = client.getIfAvailable();
        if (llm == null) {
            return new Explained(draft, RULES);
        }
        try {
            String facts = objectMapper.writeValueAsString(findings);
            String candidate = llm.rewrite(question, facts, draft);
            if (candidate == null || candidate.isBlank() || candidate.length() > MAX_LENGTH) {
                log.warn("Assistant explanation rejected: empty or too long");
                return new Explained(draft, RULES);
            }
            if (!AnswerVerifier.usesOnlyKnownNumbers(candidate, draft + " " + facts)) {
                // Deliberately not logging the text: it may contain the user's financial data.
                log.warn("Assistant explanation rejected: it contained numbers not present in the backend's facts");
                return new Explained(draft, RULES);
            }
            return new Explained(candidate, LLM);
        } catch (JsonProcessingException | RuntimeException exception) {
            log.warn("Assistant explanation unavailable, using the deterministic answer: {}", exception.getClass().getSimpleName());
            return new Explained(draft, RULES);
        }
    }
}

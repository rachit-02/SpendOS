package com.spendos.assistant.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public final class AssistantDtos {

    private AssistantDtos() {
    }

    /** The month the user is looking at in the UI; "this month" in a question refers to it. */
    public record AssistantContext(@Size(max = 20) String selectedMonth, @Min(1990) @Max(2200) Integer selectedYear) {
    }

    public record QueryRequest(@NotBlank @Size(max = 500) String question, @Valid AssistantContext context) {
    }

    /**
     * One fact behind an answer. Comparisons use {@code thisMonth}/{@code previousMonth}/{@code change}
     * (API_DESIGN.md); other answers use {@code amount}, {@code percentage}, {@code count} and
     * {@code frequency}. Absent fields are omitted from the JSON.
     */
    public record Finding(String category, String merchant, BigDecimal thisMonth, BigDecimal previousMonth,
                          BigDecimal change, BigDecimal changePercentage, BigDecimal amount, BigDecimal percentage,
                          Long count, String frequency) {

        public static Finding comparison(String category, String merchant, BigDecimal thisMonth, BigDecimal previousMonth,
                                         BigDecimal change, BigDecimal changePercentage) {
            return new Finding(category, merchant, thisMonth, previousMonth, change, changePercentage, null, null, null, null);
        }

        public static Finding amount(String category, String merchant, BigDecimal amount, BigDecimal percentage, Long count) {
            return new Finding(category, merchant, null, null, null, null, amount, percentage, count, null);
        }

        public static Finding recurring(String merchant, BigDecimal amount, String frequency) {
            return new Finding(null, merchant, null, null, null, null, amount, null, null, frequency);
        }
    }

    public record RelatedTransaction(UUID id, String merchant, String description, String category, BigDecimal amount,
                                     LocalDate date) {
    }

    public record SupportingData(List<Finding> keyFindings, List<RelatedTransaction> relatedTransactions) {
    }

    /**
     * {@code answerSource} is "rules" when the answer is the deterministic template, or "llm" when a
     * language model reworded it and every number it used was verified against the backend's figures.
     */
    public record AssistantAnswer(String question, String intent, String period, String answer,
                                  SupportingData supportingData, List<String> followUpQuestions, String answerSource,
                                  String currencyCode) {
    }

    public record Suggestion(String question, String intent) {
    }
}

package com.spendos.assistant.llm;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Factuality guard for model output: every number in the candidate text must already appear in the
 * backend's facts (the draft answer and findings). Rounding to the nearest whole unit or to one
 * decimal is tolerated; anything else, including a number the model invented, rejects the text.
 */
public final class AnswerVerifier {

    private static final Pattern NUMBER = Pattern.compile("\\d[\\d,]*(?:\\.\\d+)?");
    private static final BigDecimal TOLERANCE = new BigDecimal("0.5");

    private AnswerVerifier() {
    }

    public static boolean usesOnlyKnownNumbers(String candidate, String facts) {
        List<BigDecimal> known = numbers(facts);
        return numbers(candidate).stream().allMatch(n -> known.stream()
                .anyMatch(k -> k.subtract(n).abs().compareTo(TOLERANCE) <= 0));
    }

    static List<BigDecimal> numbers(String text) {
        List<BigDecimal> numbers = new ArrayList<>();
        Matcher matcher = NUMBER.matcher(text == null ? "" : text);
        while (matcher.find()) {
            String digits = matcher.group().replace(",", "");
            if (digits.endsWith(".")) {
                digits = digits.substring(0, digits.length() - 1);
            }
            numbers.add(new BigDecimal(digits));
        }
        return numbers;
    }
}

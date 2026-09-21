package com.spendos.assistant;

import static org.assertj.core.api.Assertions.assertThat;

import com.spendos.assistant.engine.Intent;
import com.spendos.assistant.engine.QuestionParser;
import com.spendos.assistant.engine.QuestionParser.CategoryRef;
import com.spendos.assistant.engine.QuestionParser.MerchantRef;
import com.spendos.assistant.engine.QuestionParser.ParsedQuestion;
import com.spendos.assistant.engine.QuestionParser.Vocabulary;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class QuestionParserTest {

    private static final CategoryRef FOOD = new CategoryRef(UUID.randomUUID(), "Food", null, null);
    private static final CategoryRef GROCERIES = new CategoryRef(FOOD.id(), "Food", UUID.randomUUID(), "Groceries");
    private static final CategoryRef SUBSCRIPTIONS = new CategoryRef(UUID.randomUUID(), "Subscriptions", null, null);
    private static final MerchantRef ZOMATO = new MerchantRef(UUID.randomUUID(), "Zomato");
    private static final MerchantRef AMAZON_PAY = new MerchantRef(UUID.randomUUID(), "Amazon Pay");
    private static final MerchantRef AMAZON = new MerchantRef(UUID.randomUUID(), "Amazon");
    private static final Vocabulary VOCABULARY = new Vocabulary(
            Map.of("food", FOOD, "eating out", FOOD, "groceries", GROCERIES, "grocery", GROCERIES,
                    "subscriptions", SUBSCRIPTIONS, "subscription", SUBSCRIPTIONS),
            List.of(ZOMATO, AMAZON, AMAZON_PAY));
    private static final YearMonth SEPTEMBER = YearMonth.of(2026, 9);

    private static ParsedQuestion parse(String question) {
        return QuestionParser.parse(question, VOCABULARY, SEPTEMBER, SEPTEMBER);
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "Why did I spend more this month?|SPENDING_CHANGE",
            "Why did my spending go up?|SPENDING_CHANGE",
            "What drove the jump in expenses?|SPENDING_CHANGE",
            "Where did most of my money go?|TOP_CATEGORIES",
            "What are my top 3 categories?|TOP_CATEGORIES",
            "How much did I spend on food?|CATEGORY_SPEND",
            "How much did I spend on subscriptions?|CATEGORY_SPEND",
            "What were my largest purchases?|LARGEST_PURCHASES",
            "Show my 3 biggest transactions last month|LARGEST_PURCHASES",
            "Which subscriptions do I have?|SUBSCRIPTIONS",
            "What recurring payments am I paying for?|SUBSCRIPTIONS",
            "Compare August and September.|COMPARE_MONTHS",
            "August vs July|COMPARE_MONTHS",
            "How much did I spend at Zomato?|MERCHANT_SPEND",
            "How much did I save this month?|MONTH_SUMMARY",
            "What was my income in June?|MONTH_SUMMARY",
            "What's the weather tomorrow?|UNKNOWN",
    })
    void detectsTheSupportedQuestions(String question, Intent intent) {
        assertThat(parse(question).intent()).isEqualTo(intent);
    }

    @Test
    void extractsMonthsInOrderAndResolvesThemToTheMostRecentOccurrence() {
        assertThat(parse("Compare August and September").months())
                .containsExactly(YearMonth.of(2026, 8), YearMonth.of(2026, 9));
        // December has not happened yet this year, so it means last December.
        assertThat(parse("How much did I spend in December?").months()).containsExactly(YearMonth.of(2025, 12));
        assertThat(parse("Compare Jan 2025 and sept 2025").months())
                .containsExactly(YearMonth.of(2025, 1), YearMonth.of(2025, 9));
        assertThat(parse("How much did I spend last month?").months()).containsExactly(YearMonth.of(2026, 8));
        assertThat(parse("Why did I spend more this month?").months()).containsExactly(SEPTEMBER);
    }

    @Test
    void mayIsOnlyAMonthInContext() {
        assertThat(parse("Where may I cut spending?").months()).isEmpty();
        assertThat(parse("How much did I spend in May?").months()).containsExactly(YearMonth.of(2026, 5));
        assertThat(parse("Compare may 2025 and june 2025").months()).hasSize(2);
    }

    @Test
    void extractsCategoriesMerchantsAndLimits() {
        assertThat(parse("How much did I spend eating out?").category()).isEqualTo(FOOD);
        assertThat(parse("How much went on groceries").category()).isEqualTo(GROCERIES);
        // Longest merchant name wins, and matching is on whole words.
        assertThat(parse("How much did I pay with amazon pay?").merchant()).isEqualTo(AMAZON_PAY);
        assertThat(parse("How much did I spend at amazonia?").merchant()).isNull();
        assertThat(parse("Show my top 3 purchases").limit()).isEqualTo(3);
        assertThat(parse("Show my 7 largest expenses").limit()).isEqualTo(7);
        assertThat(parse("Show my largest purchases").limit()).isNull();
    }

    @Test
    void remembersWhetherTheUserAskedAboutSpendingLess() {
        assertThat(parse("Why did I spend less this month?").asksAboutLess()).isTrue();
        assertThat(parse("Why did I spend more this month?").asksAboutLess()).isFalse();
    }

    @Test
    void whyQuestionsWithTwoMonthsExplainTheChangeBetweenThem() {
        ParsedQuestion parsed = parse("Why did I spend more in September than August?");
        assertThat(parsed.intent()).isEqualTo(Intent.SPENDING_CHANGE);
        assertThat(parsed.months()).hasSize(2);
    }
}

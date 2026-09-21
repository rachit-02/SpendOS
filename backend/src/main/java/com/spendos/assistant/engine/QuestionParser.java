package com.spendos.assistant.engine;

import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Rule-based intent detection and entity extraction (months, category, merchant, "top N").
 * Pure and deterministic: the same question against the same reference data always parses the
 * same way, which keeps answers testable. No LLM is involved in understanding the question.
 */
public final class QuestionParser {

    public record CategoryRef(UUID id, String name, UUID subcategoryId, String subcategoryName) {
    }

    public record MerchantRef(UUID id, String name) {
    }

    /** {@code months} are in the order they appear in the question; {@code asksAboutLess} is "why did I spend less". */
    public record ParsedQuestion(Intent intent, List<YearMonth> months, CategoryRef category, MerchantRef merchant,
                                 Integer limit, boolean asksAboutLess) {
    }

    /** Reference data the parser matches against: the user's categories (with aliases) and merchants. */
    public record Vocabulary(Map<String, CategoryRef> categoryAliases, List<MerchantRef> merchants) {
    }

    private static final Map<String, Integer> MONTHS = new LinkedHashMap<>();

    static {
        String[] names = {"january", "february", "march", "april", "may", "june", "july", "august", "september",
                "october", "november", "december"};
        for (int i = 0; i < names.length; i++) {
            MONTHS.put(names[i], i + 1);
        }
        String[] abbreviations = {"jan", "feb", "mar", "apr", null, "jun", "jul", "aug", "sep", "oct", "nov", "dec"};
        for (int i = 0; i < abbreviations.length; i++) {
            if (abbreviations[i] != null) {
                MONTHS.put(abbreviations[i], i + 1);
            }
        }
        MONTHS.put("sept", 9);
    }

    private static final Pattern MONTH = Pattern.compile(
            "\\b(" + String.join("|", MONTHS.keySet()) + ")\\b(?:\\s*,?\\s*(\\d{4}))?");
    /** "may" is only a month next to a year or after a preposition ("in may"), never the verb. */
    private static final Pattern MAY_CONTEXT = Pattern.compile("\\b(in|for|of|during|and|vs|versus|between|since|to|from)\\s*$");
    private static final Pattern THIS_MONTH = Pattern.compile("\\b(this|current)\\s+month\\b");
    private static final Pattern LAST_MONTH = Pattern.compile("\\b(last|previous|past)\\s+month\\b");
    private static final Pattern LIMIT = Pattern.compile("\\b(?:top|largest|biggest|highest|first)\\s+(\\d{1,2})\\b|\\b(\\d{1,2})\\s+(?:largest|biggest|highest|top|most expensive)\\b");

    private static final Pattern COMPARE = Pattern.compile("\\b(compare|comparison|vs\\.?|versus|difference between)\\b");
    private static final Pattern WHY_CHANGE = Pattern.compile(
            "\\bwhy\\b.*\\b(spen[dt]|spending|expenses?)\\b.*\\b(more|less|increase[ds]?|higher|lower|up|down|jump(ed)?|drop(ped)?|rise|rose|decrease[ds]?)\\b"
                    + "|\\b(spending|expenses?)\\b.*\\b(go|went|gone)\\s+(up|down)\\b"
                    + "|\\bwhat\\s+(changed|drove|caused)\\b");
    private static final Pattern LESS = Pattern.compile("\\b(less|lower|down|drop(ped)?|decrease[ds]?)\\b");
    private static final Pattern SUBSCRIPTIONS = Pattern.compile("\\b(subscriptions?|subscribed|recurring|memberships?|auto-?pay|standing instructions?)\\b");
    private static final Pattern SPEND_ON = Pattern.compile("\\b(spen[dt]|spending|pay|paid)\\s+(on|for)\\b");
    private static final Pattern LARGEST = Pattern.compile(
            "\\b(largest|biggest|highest|most expensive|top|big|major|large)\\b.*\\b(purchases?|transactions?|expenses?|payments?|buys?|spends?|debits?)\\b");
    private static final Pattern WHERE_MONEY = Pattern.compile(
            "\\bwhere\\b.*\\b(money|spen[dt]|spending)\\b|\\bmost of my (money|spending)\\b|\\bcategor(y|ies)\\b|\\bbreakdown\\b|\\bsplit\\b");
    private static final Pattern SUMMARY = Pattern.compile(
            "\\b(save|saved|saving|savings|income|earn|earned|earnings|net|cash ?flow|spen[dt]|spending|expenses?|total|summary|overview)\\b");

    private QuestionParser() {
    }

    /**
     * @param reference the month "this month" refers to (the month the user is looking at)
     * @param latest    the latest month a bare month name may resolve to (usually the current month)
     */
    public static ParsedQuestion parse(String question, Vocabulary vocabulary, YearMonth reference, YearMonth latest) {
        String text = normalize(question);
        List<YearMonth> months = months(text, reference, latest);
        CategoryRef category = category(text, vocabulary.categoryAliases());
        MerchantRef merchant = merchant(text, vocabulary.merchants());
        Integer limit = limit(text);
        boolean less = LESS.matcher(text).find();

        Intent intent;
        if (WHY_CHANGE.matcher(text).find()) {
            intent = Intent.SPENDING_CHANGE;
        } else if (COMPARE.matcher(text).find() || months.size() >= 2) {
            intent = Intent.COMPARE_MONTHS;
        } else if (SUBSCRIPTIONS.matcher(text).find() && !SPEND_ON.matcher(text).find()) {
            intent = Intent.SUBSCRIPTIONS;
        } else if (LARGEST.matcher(text).find() && !text.contains("categor")) {
            intent = Intent.LARGEST_PURCHASES;
        } else if (merchant != null) {
            intent = Intent.MERCHANT_SPEND;
        } else if (category != null) {
            intent = Intent.CATEGORY_SPEND;
        } else if (WHERE_MONEY.matcher(text).find()) {
            intent = Intent.TOP_CATEGORIES;
        } else if (SUMMARY.matcher(text).find()) {
            intent = Intent.MONTH_SUMMARY;
        } else {
            intent = Intent.UNKNOWN;
        }
        return new ParsedQuestion(intent, months, category, merchant, limit, less);
    }

    static String normalize(String question) {
        return question.toLowerCase(Locale.ROOT).replace('’', '\'').replaceAll("[?!]", " ").replaceAll("\\s+", " ").trim();
    }

    static List<YearMonth> months(String text, YearMonth reference, YearMonth latest) {
        record Found(int position, YearMonth month) {
        }
        List<Found> found = new ArrayList<>();
        Matcher matcher = MONTH.matcher(text);
        while (matcher.find()) {
            String name = matcher.group(1);
            String year = matcher.group(2);
            if (name.equals("may") && year == null && !MAY_CONTEXT.matcher(text.substring(0, matcher.start())).find()) {
                continue;
            }
            int month = MONTHS.get(name);
            YearMonth resolved;
            if (year != null) {
                resolved = YearMonth.of(Integer.parseInt(year), month);
            } else {
                // A bare month name means its most recent occurrence.
                resolved = YearMonth.of(latest.getYear(), month);
                if (resolved.isAfter(latest)) {
                    resolved = resolved.minusYears(1);
                }
            }
            found.add(new Found(matcher.start(), resolved));
        }
        Matcher thisMonth = THIS_MONTH.matcher(text);
        while (thisMonth.find()) {
            found.add(new Found(thisMonth.start(), reference));
        }
        Matcher lastMonth = LAST_MONTH.matcher(text);
        while (lastMonth.find()) {
            found.add(new Found(lastMonth.start(), reference.minusMonths(1)));
        }
        return found.stream()
                .sorted(Comparator.comparingInt(Found::position))
                .map(Found::month)
                .distinct()
                .toList();
    }

    static CategoryRef category(String text, Map<String, CategoryRef> aliases) {
        // Longest alias first so "food delivery" beats "food".
        return aliases.entrySet().stream()
                .sorted(Comparator.comparingInt((Map.Entry<String, CategoryRef> e) -> e.getKey().length()).reversed())
                .filter(e -> containsWord(text, e.getKey()))
                .map(Map.Entry::getValue)
                .findFirst()
                .orElse(null);
    }

    static MerchantRef merchant(String text, List<MerchantRef> merchants) {
        return merchants.stream()
                .filter(m -> m.name() != null && m.name().length() >= 3)
                .sorted(Comparator.comparingInt((MerchantRef m) -> m.name().length()).reversed())
                .filter(m -> containsWord(text, m.name().toLowerCase(Locale.ROOT)))
                .findFirst()
                .orElse(null);
    }

    static Integer limit(String text) {
        Matcher matcher = LIMIT.matcher(text);
        if (!matcher.find()) {
            return null;
        }
        int value = Integer.parseInt(matcher.group(1) != null ? matcher.group(1) : matcher.group(2));
        return value >= 1 && value <= 20 ? value : null;
    }

    private static boolean containsWord(String text, String word) {
        return Pattern.compile("(?<![\\p{L}\\p{N}])" + Pattern.quote(word) + "(?![\\p{L}\\p{N}])").matcher(text).find();
    }
}

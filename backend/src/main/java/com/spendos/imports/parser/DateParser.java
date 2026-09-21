package com.spendos.imports.parser;

import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.Month;
import java.util.Collection;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parses the date formats found in bank statements: numeric (DD-MM-YYYY, MM/DD/YYYY, YYYY-MM-DD,
 * DD.MM.YY ...), and month-name forms (05 Sep 2026, 05-Sep-26, Sep 5, 2026). Any trailing time is ignored.
 * Day/month order for ambiguous numeric dates is decided once per file by {@link #detectOrder}.
 */
public final class DateParser {

    /** Order of day and month in numeric dates where the year comes last. */
    public enum Order { DMY, MDY }

    private static final Pattern YEAR_FIRST = Pattern.compile("^(\\d{4})[-/.](\\d{1,2})[-/.](\\d{1,2})");
    private static final Pattern YEAR_LAST = Pattern.compile("^(\\d{1,2})[-/. ](\\d{1,2})[-/. ](\\d{2}|\\d{4})(?!\\d)");
    private static final Pattern DAY_MONTH_NAME = Pattern.compile("^(\\d{1,2})[-/. ]?([A-Za-z]{3,9})[-/., ]*(\\d{2}|\\d{4})(?!\\d)");
    private static final Pattern MONTH_NAME_DAY = Pattern.compile("^([A-Za-z]{3,9})[-/. ]+(\\d{1,2})(?:st|nd|rd|th)?,?[-/. ]+(\\d{2}|\\d{4})(?!\\d)");
    private static final Pattern COMPACT = Pattern.compile("^(\\d{4})(\\d{2})(\\d{2})$");

    private static final Map<String, Month> MONTHS = Map.ofEntries(
            Map.entry("jan", Month.JANUARY), Map.entry("feb", Month.FEBRUARY), Map.entry("mar", Month.MARCH),
            Map.entry("apr", Month.APRIL), Map.entry("may", Month.MAY), Map.entry("jun", Month.JUNE),
            Map.entry("jul", Month.JULY), Map.entry("aug", Month.AUGUST), Map.entry("sep", Month.SEPTEMBER),
            Map.entry("sept", Month.SEPTEMBER), Map.entry("oct", Month.OCTOBER), Map.entry("nov", Month.NOVEMBER),
            Map.entry("dec", Month.DECEMBER));

    private DateParser() {
    }

    /** Maps the optional upload hint ("DD-MM-YYYY", "MM/DD/YYYY", ...) to an order; null when absent/unknown. */
    public static Order orderFromHint(String hint) {
        if (hint == null || hint.isBlank()) {
            return null;
        }
        String normalized = hint.toUpperCase(Locale.ROOT).replaceAll("[^DMY]", "");
        if (normalized.startsWith("DDMM") || normalized.startsWith("DM")) {
            return Order.DMY;
        }
        if (normalized.startsWith("MMDD") || normalized.startsWith("MD")) {
            return Order.MDY;
        }
        return null;
    }

    /**
     * Decides day/month order from the sample: a first component above 12 proves DMY, a second above 12
     * proves MDY. Ambiguous files use the hint, else DMY (the Indian convention).
     */
    public static Order detectOrder(Collection<String> samples, Order hint) {
        boolean firstAbove12 = false;
        boolean secondAbove12 = false;
        for (String sample : samples) {
            if (sample == null) {
                continue;
            }
            Matcher matcher = YEAR_LAST.matcher(sample.trim());
            if (matcher.find()) {
                firstAbove12 |= Integer.parseInt(matcher.group(1)) > 12;
                secondAbove12 |= Integer.parseInt(matcher.group(2)) > 12;
            }
        }
        if (firstAbove12 && !secondAbove12) {
            return Order.DMY;
        }
        if (secondAbove12 && !firstAbove12) {
            return Order.MDY;
        }
        return hint != null ? hint : Order.DMY;
    }

    /** Returns the parsed date, or null when the value is not a recognizable date. */
    public static LocalDate parse(String value, Order order) {
        if (value == null) {
            return null;
        }
        String text = value.trim();
        if (text.isEmpty()) {
            return null;
        }
        try {
            Matcher m = YEAR_FIRST.matcher(text);
            if (m.find()) {
                return LocalDate.of(Integer.parseInt(m.group(1)), Integer.parseInt(m.group(2)), Integer.parseInt(m.group(3)));
            }
            m = COMPACT.matcher(text);
            if (m.find()) {
                return LocalDate.of(Integer.parseInt(m.group(1)), Integer.parseInt(m.group(2)), Integer.parseInt(m.group(3)));
            }
            m = DAY_MONTH_NAME.matcher(text);
            if (m.find()) {
                Month month = month(m.group(2));
                return month == null ? null : LocalDate.of(year(m.group(3)), month, Integer.parseInt(m.group(1)));
            }
            m = MONTH_NAME_DAY.matcher(text);
            if (m.find()) {
                Month month = month(m.group(1));
                return month == null ? null : LocalDate.of(year(m.group(3)), month, Integer.parseInt(m.group(2)));
            }
            m = YEAR_LAST.matcher(text);
            if (m.find()) {
                int first = Integer.parseInt(m.group(1));
                int second = Integer.parseInt(m.group(2));
                int year = year(m.group(3));
                return order == Order.MDY ? LocalDate.of(year, first, second) : LocalDate.of(year, second, first);
            }
        } catch (DateTimeException | NumberFormatException exception) {
            return null;
        }
        return null;
    }

    public static boolean looksLikeDate(String value) {
        return parse(value, Order.DMY) != null || parse(value, Order.MDY) != null;
    }

    private static Month month(String name) {
        String key = name.toLowerCase(Locale.ROOT);
        Month month = MONTHS.get(key.length() > 4 ? key.substring(0, 3) : key);
        if (month == null && key.length() >= 3) {
            month = MONTHS.get(key.substring(0, 3));
        }
        return month;
    }

    /** Two-digit years map to 2000-2099 (statements never predate 1990, see TransactionService). */
    private static int year(String digits) {
        int year = Integer.parseInt(digits);
        return digits.length() == 2 ? 2000 + year : year;
    }
}

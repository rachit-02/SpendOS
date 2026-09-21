package com.spendos.imports.parser;

import java.math.BigDecimal;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Parses statement amounts: "1,234.50", "₹ 1,234.50", "Rs. 450", "(450.00)", "-450", "450-",
 * "450.00 Dr", "1.234,50" (decimal comma). Returns a signed value and, when present, a Dr/Cr marker.
 */
public final class AmountParser {

    /** Parsed amount; {@code marker} is "debit"/"credit" when the text carried Dr/Cr, else null. */
    public record ParsedAmount(BigDecimal value, String marker) {
    }

    private static final Pattern CURRENCY = Pattern.compile("(?i)(₹|rs\\.?|inr|usd|\\$|€|£|eur|gbp)");
    private static final Pattern DR_CR = Pattern.compile("(?i)\\b(dr|cr|debit|credit)\\.?$");
    private static final Pattern VALID = Pattern.compile("^\\d+(\\.\\d+)?$");

    private AmountParser() {
    }

    /** Returns the parsed amount, or null when the text is empty or not a number. */
    public static ParsedAmount parse(String raw) {
        if (raw == null) {
            return null;
        }
        String text = raw.trim();
        if (text.isEmpty() || text.equals("-")) {
            return null;
        }
        String marker = null;
        var drCr = DR_CR.matcher(text);
        if (drCr.find()) {
            String token = drCr.group(1).toLowerCase(Locale.ROOT);
            marker = token.startsWith("d") ? "debit" : "credit";
            text = text.substring(0, drCr.start()).trim();
        }
        text = CURRENCY.matcher(text).replaceAll("").replace("\u00a0", "").replace(" ", "").trim();
        boolean negative = false;
        if (text.startsWith("(") && text.endsWith(")")) {
            negative = true;
            text = text.substring(1, text.length() - 1);
        }
        if (text.startsWith("-")) {
            negative = !negative;
            text = text.substring(1);
        } else if (text.endsWith("-")) {
            negative = !negative;
            text = text.substring(0, text.length() - 1);
        } else if (text.startsWith("+")) {
            text = text.substring(1);
        }
        text = normalizeSeparators(text);
        if (!VALID.matcher(text).matches()) {
            return null;
        }
        BigDecimal value = new BigDecimal(text);
        return new ParsedAmount(negative ? value.negate() : value, marker);
    }

    /** Resolves thousands/decimal separators into a plain "1234.50" string. */
    static String normalizeSeparators(String text) {
        int lastComma = text.lastIndexOf(',');
        int lastDot = text.lastIndexOf('.');
        if (lastComma > lastDot) {
            // "1.234,50" or "450,5" -> comma is the decimal separator when followed by 1-2 digits
            int decimals = text.length() - lastComma - 1;
            if (decimals >= 1 && decimals <= 2) {
                return text.replace(".", "").replace(",", ".");
            }
            return text.replace(",", "");
        }
        return text.replace(",", "");
    }
}

package com.spendos.merchants.normalizer;

import java.util.Arrays;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Deterministic clean-up of raw statement text into a readable merchant name, e.g.
 * "UPI-ZOMATO ONLINE-9876543210@okaxis-REF12345678" -> "Zomato Online".
 */
public final class MerchantTextCleaner {

    private static final Pattern CHANNEL_PREFIX = Pattern.compile(
            "^(?:(?:UPI|POS|NEFT|IMPS|RTGS|ACH|ECOM|ECS|NACH|BIL|BILLPAY|VPS|MPS|INB|ATW|NFS|IB|MB|TPT|CMS|DEBIT CARD|CREDIT CARD)"
                    + "(?:[\\s/:\\-_*]+|$))+", Pattern.CASE_INSENSITIVE);
    private static final Pattern VPA = Pattern.compile("\\S+@\\S+");
    private static final Pattern LONG_DIGITS = Pattern.compile("\\b\\d{5,}\\b");
    private static final Pattern REFERENCE_TOKEN = Pattern.compile("\\b(?=[A-Z0-9]*\\d)(?=[A-Z0-9]*[A-Z])[A-Z0-9]{8,}\\b",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern NOISE_WORDS = Pattern.compile(
            "\\b(?:REF|REFNO|TXN|TXNID|UTR|RRN|PAYMENT|PAYMENTS|PVT|PRIVATE|LTD|LIMITED|LLP|INC|CO|INDIA|IN|PAY TO|PAID TO|TO|FROM|VIA)\\b\\.?",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern SEPARATORS = Pattern.compile("[/\\\\_*#:|~\\-]+");
    private static final Pattern NON_TEXT = Pattern.compile("[^\\p{L}\\p{N}&.'+\\s]");
    private static final Set<String> KEEP_UPPER = Set.of("ATM", "EMI", "LIC", "SBI", "HDFC", "ICICI", "IRCTC", "KFC", "PVR");

    private MerchantTextCleaner() {
    }

    public static String clean(String raw) {
        if (raw == null || raw.isBlank()) {
            return "Unknown";
        }
        String text = raw.trim();
        text = CHANNEL_PREFIX.matcher(text).replaceFirst("");
        // Split on separators first so a VPA ("98765@okaxis") cannot swallow adjacent words.
        text = SEPARATORS.matcher(text).replaceAll(" ");
        text = VPA.matcher(text).replaceAll(" ");
        text = LONG_DIGITS.matcher(text).replaceAll(" ");
        text = REFERENCE_TOKEN.matcher(text).replaceAll(" ");
        text = NON_TEXT.matcher(text).replaceAll(" ");
        String withoutNoise = NOISE_WORDS.matcher(text).replaceAll(" ").trim();
        if (!withoutNoise.isBlank()) {
            text = withoutNoise;
        }
        text = text.replaceAll("\\s+", " ").trim();
        if (text.isEmpty()) {
            return "Unknown";
        }
        if (text.length() > 60) {
            text = text.substring(0, 60).trim();
        }
        return titleCase(text);
    }

    static String titleCase(String text) {
        return Arrays.stream(text.split(" "))
                .map(word -> {
                    String upper = word.toUpperCase(Locale.ROOT);
                    if (KEEP_UPPER.contains(upper) || word.length() <= 1) {
                        return upper;
                    }
                    return upper.charAt(0) + word.substring(1).toLowerCase(Locale.ROOT);
                })
                .collect(Collectors.joining(" "));
    }
}

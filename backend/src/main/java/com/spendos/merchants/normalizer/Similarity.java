package com.spendos.merchants.normalizer;

import java.util.Locale;

/** Levenshtein-based similarity for merchant names (0 = unrelated, 1 = identical, case-insensitive). */
public final class Similarity {

    private Similarity() {
    }

    public static int distance(String a, String b) {
        int[] previous = new int[b.length() + 1];
        int[] current = new int[b.length() + 1];
        for (int j = 0; j <= b.length(); j++) {
            previous[j] = j;
        }
        for (int i = 1; i <= a.length(); i++) {
            current[0] = i;
            for (int j = 1; j <= b.length(); j++) {
                int cost = a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1;
                current[j] = Math.min(Math.min(current[j - 1] + 1, previous[j] + 1), previous[j - 1] + cost);
            }
            int[] swap = previous;
            previous = current;
            current = swap;
        }
        return previous[b.length()];
    }

    /** 1 - distance / longer length, on lower-cased, whitespace-collapsed text. */
    public static double ratio(String a, String b) {
        String x = normalize(a);
        String y = normalize(b);
        int longest = Math.max(x.length(), y.length());
        return longest == 0 ? 1.0 : 1.0 - (double) distance(x, y) / longest;
    }

    /**
     * The better of whole-name similarity and the best single word of {@code raw} against
     * {@code candidate}, so "ZOMATO ONLINE ORDER" still scores highly against "Zomato". Words
     * shorter than four letters are ignored to avoid matching on noise like "upi" or "pvt".
     */
    public static double score(String raw, String candidate) {
        double best = ratio(raw, candidate);
        String target = normalize(candidate);
        for (String word : normalize(raw).split("[^\\p{L}\\p{N}]+")) {
            if (word.length() >= 4 && !target.contains(" ")) {
                best = Math.max(best, ratio(word, target));
            }
        }
        return best;
    }

    private static String normalize(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT).trim().replaceAll("\\s+", " ");
    }
}

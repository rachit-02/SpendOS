package com.spendos.merchants.normalizer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import org.junit.jupiter.api.Test;

class SimilarityTest {

    @Test
    void levenshteinDistance() {
        assertThat(Similarity.distance("kitten", "sitting")).isEqualTo(3);
        assertThat(Similarity.distance("", "abc")).isEqualTo(3);
        assertThat(Similarity.distance("swiggy", "swiggy")).isZero();
    }

    @Test
    void ratioIsCaseInsensitiveAndNormalised() {
        assertThat(Similarity.ratio("SWIGY", "Swiggy")).isCloseTo(0.833, within(0.001));
        assertThat(Similarity.ratio("Zomato", "zomato ")).isEqualTo(1.0);
        assertThat(Similarity.ratio("", "")).isEqualTo(1.0);
        assertThat(Similarity.ratio("Landlord", "Swiggy")).isLessThan(0.3);
    }

    @Test
    void scoreMatchesOnTheBestWordButIgnoresShortNoise() {
        assertThat(Similarity.score("ZOMATO ONLINE ORDER", "Zomato")).isEqualTo(1.0);
        assertThat(Similarity.score("Swigy Instamart", "Swiggy")).isCloseTo(0.833, within(0.001));
        // "upi" is too short to count as a word match on its own.
        assertThat(Similarity.score("UPI XYZ", "UPI")).isLessThan(0.6);
    }
}

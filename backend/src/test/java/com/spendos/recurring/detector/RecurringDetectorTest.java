package com.spendos.recurring.detector;

import static org.assertj.core.api.Assertions.assertThat;

import com.spendos.recurring.detector.RecurringDetector.Detection;
import com.spendos.recurring.detector.RecurringDetector.Frequency;
import com.spendos.recurring.detector.RecurringDetector.Payment;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

class RecurringDetectorTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 9, 21);

    private static Payment pay(String merchant, String amount, LocalDate date) {
        return new Payment(UUID.randomUUID(), merchant, null, merchant, null, new BigDecimal(amount), date);
    }

    private static List<Payment> monthly(String merchant, String amount, int months, LocalDate last) {
        List<Payment> payments = new ArrayList<>();
        for (int i = months - 1; i >= 0; i--) {
            payments.add(pay(merchant, amount, last.minusMonths(i)));
        }
        return payments;
    }

    @Test
    void netflixIsDetectedAsHighConfidenceMonthly() {
        List<Detection> detections = RecurringDetector.detect(monthly("Netflix", "499", 12, LocalDate.of(2026, 9, 5)), TODAY);

        assertThat(detections).singleElement().satisfies(d -> {
            assertThat(d.frequency()).isEqualTo(Frequency.MONTHLY);
            assertThat(d.confidence()).isEqualByComparingTo("1.00");
            assertThat(d.typicalAmount()).isEqualByComparingTo("499");
            assertThat(d.nextExpectedDate()).isEqualTo(LocalDate.of(2026, 10, 5));
            assertThat(d.occurrences()).isEqualTo(12);
            assertThat(d.active()).isTrue();
        });
    }

    @Test
    void weeklyAndAnnualPaymentsAreClassified() {
        List<Payment> payments = new ArrayList<>();
        for (int i = 0; i < 8; i++) {
            payments.add(pay("Laundry", "300", TODAY.minusDays(7L * i + 2)));
        }
        payments.add(pay("Amazon Prime", "1499", LocalDate.of(2025, 3, 10)));
        payments.add(pay("Amazon Prime", "1499", LocalDate.of(2026, 3, 10)));

        List<Detection> detections = RecurringDetector.detect(payments, TODAY);

        assertThat(detections).extracting(Detection::merchantName, Detection::frequency)
                .containsExactlyInAnyOrder(
                        org.assertj.core.groups.Tuple.tuple("Laundry", Frequency.WEEKLY),
                        org.assertj.core.groups.Tuple.tuple("Amazon Prime", Frequency.ANNUAL));
    }

    @Test
    void varyingBillsAreDetectedWithLowerConfidence() {
        List<Payment> electricity = List.of(
                pay("BESCOM", "1450", LocalDate.of(2026, 4, 12)), pay("BESCOM", "1720", LocalDate.of(2026, 5, 12)),
                pay("BESCOM", "1980", LocalDate.of(2026, 6, 11)), pay("BESCOM", "1610", LocalDate.of(2026, 7, 12)),
                pay("BESCOM", "1500", LocalDate.of(2026, 8, 12)), pay("BESCOM", "1390", LocalDate.of(2026, 9, 12)));

        Detection detection = RecurringDetector.detect(electricity, TODAY).get(0);
        assertThat(detection.frequency()).isEqualTo(Frequency.MONTHLY);
        assertThat(detection.confidence()).isBetween(new BigDecimal("0.60"), new BigDecimal("0.99"));
    }

    @Test
    void irregularFoodOrdersAreNotRecurring() {
        Random random = new Random(7);
        List<Payment> orders = new ArrayList<>();
        LocalDate date = TODAY.minusDays(120);
        while (date.isBefore(TODAY)) {
            orders.add(pay("Zomato", String.valueOf(150 + random.nextInt(700)), date));
            date = date.plusDays(1 + random.nextInt(6));
        }
        assertThat(RecurringDetector.detect(orders, TODAY)).isEmpty();
    }

    @Test
    void stoppedSubscriptionIsReportedInactive() {
        Detection detection = RecurringDetector.detect(monthly("Old Gym", "1500", 6, LocalDate.of(2026, 4, 1)), TODAY).get(0);
        assertThat(detection.active()).isFalse();
    }

    @Test
    void twoPaymentsAreNotEnoughForMonthly() {
        assertThat(RecurringDetector.detect(monthly("Once", "100", 2, LocalDate.of(2026, 9, 1)), TODAY)).isEmpty();
    }

    /** Synthetic year of data: 10 real recurring payments among 10 noisy merchants; >90% precision and recall. */
    @Test
    void detectionAccuracyOnMixedSyntheticDataIsAboveNinetyPercent() {
        Random random = new Random(2026);
        List<Payment> payments = new ArrayList<>();
        Set<String> truth = Set.of("Netflix", "Spotify", "Rent", "Gym", "Phone", "Internet", "Insurance",
                "Cloud Storage", "Newspaper", "Maid");
        String[] amounts = {"499", "119", "25000", "1500", "599", "899", "2400", "130", "300", "6000"};
        String[] names = truth.stream().sorted().toArray(String[]::new);
        for (int i = 0; i < names.length; i++) {
            int dayJitter = random.nextInt(3);
            for (int m = 11; m >= 0; m--) {
                LocalDate date = LocalDate.of(2026, 9, 3 + i).minusMonths(m).plusDays(random.nextInt(3) - dayJitter / 2);
                payments.add(pay(names[i], amounts[i], date));
            }
        }
        String[] noise = {"Zomato", "Swiggy", "Amazon", "Uber", "Starbucks", "Kirana", "Petrol", "Pharmacy", "Movies", "Myntra"};
        for (String merchant : noise) {
            int count = 3 + random.nextInt(25);
            for (int k = 0; k < count; k++) {
                payments.add(pay(merchant, String.valueOf(100 + random.nextInt(3000)), TODAY.minusDays(random.nextInt(360))));
            }
        }

        Set<String> detected = RecurringDetector.detect(payments, TODAY).stream()
                .map(Detection::merchantName).collect(Collectors.toSet());
        long truePositives = detected.stream().filter(truth::contains).count();
        double precision = detected.isEmpty() ? 0 : (double) truePositives / detected.size();
        double recall = (double) truePositives / truth.size();

        assertThat(precision).as("precision").isGreaterThanOrEqualTo(0.9);
        assertThat(recall).as("recall").isGreaterThanOrEqualTo(0.9);
    }
}

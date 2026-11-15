package com.llijat.t212visualizer.service;

import com.llijat.t212visualizer.dto.Subscription;
import com.llijat.t212visualizer.model.CardExpense;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Written BEFORE the code (TDD): these tests are the specification of SubscriptionDetector.
 * The rules: at least 3 payments to the same merchant; the last 3 between 25 and 35 days apart;
 * their amounts at most 10 % away from the last one; and the last one at most 40 days before today.
 */
class SubscriptionDetectorTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 11, 15);

    private int counter = 0;

    // A card payment at midday UTC (early afternoon in Spain), stored as a negative amount like T212 does
    private CardExpense payment(String merchant, String amount, String date) {
        counter++;
        return new CardExpense("ref-" + counter, "Card debit", Instant.parse(date + "T12:00:00Z"),
                new BigDecimal(amount).negate(), "EUR", merchant, "ENTERTAINMENT");
    }

    private static Subscription only(List<Subscription> found) {
        assertEquals(1, found.size(), "expected exactly one subscription but found " + found);
        return found.get(0);
    }

    // ===== What is a subscription =====

    @Test
    void theSameAmountEveryMonth_isASubscription() {
        List<CardExpense> expenses = List.of(
                payment("Music Streaming", "10.99", "2026-08-03"),
                payment("Music Streaming", "10.99", "2026-09-03"),
                payment("Music Streaming", "10.99", "2026-10-03"),
                payment("Music Streaming", "10.99", "2026-11-03"));

        Subscription subscription = only(SubscriptionDetector.detect(expenses, TODAY));

        assertEquals("Music Streaming", subscription.merchant());
        assertEquals(new BigDecimal("10.99"), subscription.amount());
        assertEquals(LocalDate.of(2026, 11, 3), subscription.lastPayment());
        assertEquals(LocalDate.of(2026, 12, 3), subscription.nextPayment());
        assertFalse(subscription.priceChanged());
    }

    @Test
    void exactlyThreeMonthlyPayments_areEnough() {
        List<CardExpense> expenses = List.of(
                payment("Cloud Storage", "2.99", "2026-09-08"),
                payment("Cloud Storage", "2.99", "2026-10-08"),
                payment("Cloud Storage", "2.99", "2026-11-08"));

        assertEquals(1, SubscriptionDetector.detect(expenses, TODAY).size());
    }

    @Test
    void thePaymentsMayComeInAnyOrder() {
        // The repository does not promise any order: the detector must sort them by date
        List<CardExpense> expenses = List.of(
                payment("Cloud Storage", "2.99", "2026-11-08"),
                payment("Cloud Storage", "2.99", "2026-09-08"),
                payment("Cloud Storage", "2.99", "2026-10-08"));

        assertEquals(LocalDate.of(2026, 11, 8), only(SubscriptionDetector.detect(expenses, TODAY)).lastPayment());
    }

    @Test
    void shortAndLongMonths_stillCountAsMonthly() {
        // 31 Aug -> 30 Sep is 30 days, 30 Sep -> 31 Oct is 31 days; and the next one is 30 Nov, not 1 Dec
        List<CardExpense> expenses = List.of(
                payment("Gym", "29.90", "2026-08-31"),
                payment("Gym", "29.90", "2026-09-30"),
                payment("Gym", "29.90", "2026-10-31"));

        assertEquals(LocalDate.of(2026, 11, 30), only(SubscriptionDetector.detect(expenses, TODAY)).nextPayment());
    }

    // ===== What is not =====

    @Test
    void twoPayments_areNotEnough() {
        // A haircut a month apart, twice, is a coincidence
        List<CardExpense> expenses = List.of(
                payment("Barber", "15.00", "2026-10-02"),
                payment("Barber", "15.00", "2026-11-01"));

        assertTrue(SubscriptionDetector.detect(expenses, TODAY).isEmpty());
    }

    @Test
    void weeklyShopping_isNotASubscription() {
        List<CardExpense> expenses = List.of(
                payment("Supermarket", "40.00", "2026-10-18"),
                payment("Supermarket", "40.00", "2026-10-25"),
                payment("Supermarket", "40.00", "2026-11-01"),
                payment("Supermarket", "40.00", "2026-11-08"));

        assertTrue(SubscriptionDetector.detect(expenses, TODAY).isEmpty());
    }

    @Test
    void aSubscriptionYouStoppedPaying_isNotListed() {
        // Last payment on 31 July: more than 40 days ago
        List<CardExpense> expenses = List.of(
                payment("Gym", "29.90", "2026-05-31"),
                payment("Gym", "29.90", "2026-06-30"),
                payment("Gym", "29.90", "2026-07-31"));

        assertTrue(SubscriptionDetector.detect(expenses, TODAY).isEmpty());
    }

    @Test
    void aPriceThatChangesTooMuch_isNotASubscription() {
        // 10 -> 15 is 50 % more: these are different purchases, not the same plan
        List<CardExpense> expenses = List.of(
                payment("Online Shop", "10.00", "2026-09-05"),
                payment("Online Shop", "15.00", "2026-10-05"),
                payment("Online Shop", "15.00", "2026-11-05"));

        assertTrue(SubscriptionDetector.detect(expenses, TODAY).isEmpty());
    }

    @Test
    void aMonthlyPlan_mixedWithOtherPurchases_isNotDetected() {
        // Known limitation: the extra purchases break the 30-day rhythm of the last 3 payments
        List<CardExpense> expenses = List.of(
                payment("Big Online Store", "4.99", "2026-09-10"),
                payment("Big Online Store", "4.99", "2026-10-10"),
                payment("Big Online Store", "23.50", "2026-10-21"),
                payment("Big Online Store", "4.99", "2026-11-10"));

        assertTrue(SubscriptionDetector.detect(expenses, TODAY).isEmpty());
    }

    @Test
    void differentMerchants_areNeverMixed() {
        // The same amount every month, but each from a different merchant
        List<CardExpense> expenses = List.of(
                payment("Shop A", "9.99", "2026-09-01"),
                payment("Shop B", "9.99", "2026-10-01"),
                payment("Shop C", "9.99", "2026-11-01"));

        assertTrue(SubscriptionDetector.detect(expenses, TODAY).isEmpty());
    }

    @Test
    void paymentsWithoutMerchant_areIgnored_withoutFailing() {
        List<CardExpense> expenses = List.of(
                payment(null, "5.00", "2026-09-01"),
                payment(null, "5.00", "2026-10-01"),
                payment(null, "5.00", "2026-11-01"));

        assertTrue(SubscriptionDetector.detect(expenses, TODAY).isEmpty());
    }

    @Test
    void noPayments_noSubscriptions() {
        assertTrue(SubscriptionDetector.detect(List.of(), TODAY).isEmpty());
    }

    // ===== The limits of each rule =====

    @Test
    void gapsOf25And35Days_areStillMonthly() {
        List<CardExpense> expenses = List.of(
                payment("Video Streaming", "7.99", "2026-09-06"),
                payment("Video Streaming", "7.99", "2026-10-01"),    // 25 days later
                payment("Video Streaming", "7.99", "2026-11-05"));   // 35 days later

        assertEquals(1, SubscriptionDetector.detect(expenses, TODAY).size());
    }

    @Test
    void aGapOf24Days_isTooShort() {
        List<CardExpense> expenses = List.of(
                payment("Video Streaming", "7.99", "2026-09-13"),
                payment("Video Streaming", "7.99", "2026-10-13"),
                payment("Video Streaming", "7.99", "2026-11-06"));   // 24 days later

        assertTrue(SubscriptionDetector.detect(expenses, TODAY).isEmpty());
    }

    @Test
    void aGapOf36Days_isTooLong() {
        List<CardExpense> expenses = List.of(
                payment("Video Streaming", "7.99", "2026-08-30"),
                payment("Video Streaming", "7.99", "2026-10-05"),    // 36 days later
                payment("Video Streaming", "7.99", "2026-11-04"));

        assertTrue(SubscriptionDetector.detect(expenses, TODAY).isEmpty());
    }

    @Test
    void aLastPayment40DaysAgo_isStillActive_but41IsNot() {
        // 6 October is 40 days before 15 November
        List<CardExpense> active = List.of(
                payment("Newspaper", "6.00", "2026-08-06"),
                payment("Newspaper", "6.00", "2026-09-06"),
                payment("Newspaper", "6.00", "2026-10-06"));
        List<CardExpense> stopped = List.of(
                payment("Newspaper", "6.00", "2026-08-05"),
                payment("Newspaper", "6.00", "2026-09-05"),
                payment("Newspaper", "6.00", "2026-10-05"));

        assertEquals(1, SubscriptionDetector.detect(active, TODAY).size());
        assertTrue(SubscriptionDetector.detect(stopped, TODAY).isEmpty());
    }

    @Test
    void amountsExactly10PercentAway_areStillTheSamePlan() {
        // 9.00 and 11.00 are both 10 % away from 10.00
        List<CardExpense> expenses = List.of(
                payment("Mobile Plan", "9.00", "2026-09-02"),
                payment("Mobile Plan", "11.00", "2026-10-02"),
                payment("Mobile Plan", "10.00", "2026-11-02"));

        assertEquals(1, SubscriptionDetector.detect(expenses, TODAY).size());
    }

    // ===== Price changes =====

    @Test
    void aSmallPriceRise_isASubscription_thatShowsTheOldPrice() {
        List<CardExpense> expenses = List.of(
                payment("Video Streaming", "12.99", "2026-09-12"),
                payment("Video Streaming", "12.99", "2026-10-12"),
                payment("Video Streaming", "13.99", "2026-11-12"));   // 7.7 % more

        Subscription subscription = only(SubscriptionDetector.detect(expenses, TODAY));

        assertEquals(new BigDecimal("13.99"), subscription.amount());
        assertEquals(new BigDecimal("12.99"), subscription.previousAmount());
        assertTrue(subscription.priceChanged());
    }

    // ===== Only the last 3 payments count =====

    @Test
    void aSubscriptionYouPausedAndResumed_isDetectedFromItsLast3Payments() {
        // Paid in early 2025, stopped for more than a year, resumed in September 2026
        List<CardExpense> expenses = List.of(
                payment("Language App", "8.99", "2025-01-20"),
                payment("Language App", "8.99", "2025-02-20"),
                payment("Language App", "8.99", "2025-03-20"),
                payment("Language App", "8.99", "2026-09-04"),
                payment("Language App", "8.99", "2026-10-04"),
                payment("Language App", "8.99", "2026-11-04"));

        assertEquals(LocalDate.of(2026, 11, 4), only(SubscriptionDetector.detect(expenses, TODAY)).lastPayment());
    }

    // ===== Several at once =====

    @Test
    void severalSubscriptions_areSortedByTheirNextPayment() {
        List<CardExpense> expenses = new ArrayList<>(List.of(
                payment("Music Streaming", "10.99", "2026-09-03"),
                payment("Music Streaming", "10.99", "2026-10-03"),
                payment("Music Streaming", "10.99", "2026-11-03")));
        expenses.addAll(List.of(
                payment("Gym", "29.90", "2026-08-20"),
                payment("Gym", "29.90", "2026-09-20"),
                payment("Gym", "29.90", "2026-10-20")));
        expenses.add(payment("Supermarket", "40.00", "2026-11-08"));

        List<Subscription> found = SubscriptionDetector.detect(expenses, TODAY);

        assertEquals(2, found.size());
        assertEquals("Gym", found.get(0).merchant());               // next on 20 November
        assertEquals("Music Streaming", found.get(1).merchant());   // next on 3 December
    }
}

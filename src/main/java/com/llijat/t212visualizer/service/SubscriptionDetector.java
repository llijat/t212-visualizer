package com.llijat.t212visualizer.service;

import com.llijat.t212visualizer.dto.Subscription;
import com.llijat.t212visualizer.model.CardExpense;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.*;

public class SubscriptionDetector {
    private static final ZoneId SPAIN = ZoneId.of("Europe/Madrid");
    private static final int MIN_PAYMENTS = 3;
    private static final int MIN_GAP_DAYS = 25;
    private static final int MAX_GAP_DAYS = 35;
    private static final int MAX_DAYS_SINCE_LAST = 40;
    private static final BigDecimal MAX_PRICE_CHANGE = new BigDecimal("0.10");

    private SubscriptionDetector() {}

    public static List<Subscription> detect(List<CardExpense> expenses, LocalDate today) {
        Map<String, List<CardExpense>> byMerchant = new HashMap<>();
        for (CardExpense expense : expenses) {
            if (expense.getMerchantName() == null) continue;
            byMerchant.computeIfAbsent(expense.getMerchantName(), name -> new ArrayList<>()).add(expense);
        }

        List<Subscription> subscriptions = new ArrayList<>();
        for (List<CardExpense> payments : byMerchant.values()) {
            if (payments.size() < MIN_PAYMENTS) continue;
            payments.sort(Comparator.comparing(CardExpense::getDateTime));

            CardExpense first = payments.get(payments.size() - 3);
            CardExpense second = payments.get(payments.size() - 2);
            CardExpense last = payments.get(payments.size() - 1);

            if (!isMonthlyGap(dateOf(first), dateOf(second)) || !isMonthlyGap(dateOf(second), dateOf(last))) continue;
            if (!isSimilar(amountOf(first), amountOf(last)) || !isSimilar(amountOf(second), amountOf(last))) continue;
            if (ChronoUnit.DAYS.between(dateOf(last), today) > MAX_DAYS_SINCE_LAST) continue;

            subscriptions.add(new Subscription(
                    last.getMerchantName(),
                    amountOf(last),
                    amountOf(second),
                    dateOf(last),
                    dateOf(last).plusMonths(1)));
        }
        subscriptions.sort(Comparator.comparing(Subscription::nextPayment));
        return subscriptions;
    }

    private static LocalDate dateOf(CardExpense expense) {
        return expense.getDateTime().atZone(SPAIN).toLocalDate();
    }

    private static BigDecimal amountOf(CardExpense expense) {
        return expense.getAmount().negate();
    }

    private static boolean isMonthlyGap(LocalDate from, LocalDate to) {
        long days = ChronoUnit.DAYS.between(from, to);
        return days >= MIN_GAP_DAYS && days <= MAX_GAP_DAYS;
    }

    private static boolean isSimilar(BigDecimal amount, BigDecimal reference) {
        BigDecimal difference = amount.subtract(reference).abs();
        return difference.compareTo(reference.multiply(MAX_PRICE_CHANGE)) <= 0;
    }



}

package com.llijat.t212visualizer.service;

import com.llijat.t212visualizer.model.CardExpense;
import com.llijat.t212visualizer.repository.CardExpenseRepository;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;

/** Invented card payments, so the expenses page has something to show in demo mode. */
@Component
@Profile("demo")
public class DemoCardExpenseSeeder implements ApplicationRunner {

    private static final int MONTHS = 12;

    // Fixed instead of random, so the demo looks the same every time. It makes some months higher than others.
    private static final List<BigDecimal> MONTH_FACTORS = List.of(
            new BigDecimal("1.00"), new BigDecimal("0.85"), new BigDecimal("1.10"), new BigDecimal("0.95"),
            new BigDecimal("1.25"), new BigDecimal("0.90"), new BigDecimal("1.05"), new BigDecimal("1.40"),
            new BigDecimal("0.80"), new BigDecimal("1.15"), new BigDecimal("0.95"), new BigDecimal("1.00"));

    private static final List<RecurringPayment> PAYMENTS = List.of(
            new RecurringPayment("Acme Market", "RETAIL_STORES", "62.40", 3, 1),
            new RecurringPayment("Acme Market", "RETAIL_STORES", "48.15", 17, 1),
            new RecurringPayment("Corner Bistro", "RESTAURANTS", "34.50", 8, 1),
            new RecurringPayment("Hooli Coffee", "RESTAURANTS", "4.20", 12, 1),
            new RecurringPayment("Globex Rail", "TRANSPORT", "39.90", 1, 1),
            new RecurringPayment("Initech Online Store", "RETAIL_STORES", "27.99", 22, 2),
            new RecurringPayment("Stark Fuel", "AUTOMOBILE", "55.00", 19, 2),
            new RecurringPayment("Vandelay Cinemas", "ENTERTAINMENT", "21.00", 10, 2),
            new RecurringPayment("Umbrella Barbers", "PERSONAL_SERVICES", "18.75", 14, 3),
            new RecurringPayment("Soylent Fashion", "CLOTHING_STORES", "79.90", 25, 4),
            new RecurringPayment("Tyrell Repairs", "SERVICE_PROVIDERS", "120.00", 27, 5),
            new RecurringPayment("Wayne Hotels", "HOTELS", "240.00", 20, 6),
            new RecurringPayment("Dunder Car Rental", "AUTOMOBILE_RENTAL", "65.00", 21, 6),
            new RecurringPayment("City Hall", "GOVERNMENT_SERVICES", "15.00", 5, 12));

    private final CardExpenseRepository cardExpenseRepository;

    public DemoCardExpenseSeeder(CardExpenseRepository cardExpenseRepository) {
        this.cardExpenseRepository = cardExpenseRepository;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (cardExpenseRepository.count() == 0) {
            cardExpenseRepository.saveAll(buildExpenses(LocalDate.now(ZoneOffset.UTC)));
        }
    }

    /** The payments of the last 12 months, up to today. The dates depend on today, so the demo never looks old. */
    List<CardExpense> buildExpenses(LocalDate today) {
        List<CardExpense> expenses = new ArrayList<>();
        YearMonth firstMonth = YearMonth.from(today).minusMonths(MONTHS - 1);

        for (int i = 0; i < MONTHS; i++) {
            YearMonth month = firstMonth.plusMonths(i);
            for (RecurringPayment payment : PAYMENTS) {
                LocalDate date = month.atDay(payment.dayOfMonth());
                if (i % payment.everyMonths() == 0 && !date.isAfter(today)) {
                    expenses.add(toExpense(payment, date, MONTH_FACTORS.get(i), expenses.size() + 1));
                }
            }
        }
        return expenses;
    }

    private CardExpense toExpense(RecurringPayment payment, LocalDate date, BigDecimal factor, int number) {
        BigDecimal amount = new BigDecimal(payment.amount()).multiply(factor).setScale(2, RoundingMode.HALF_UP).negate();
        return new CardExpense("demo-expense-" + number, "Card debit", date.atTime(12, 0).toInstant(ZoneOffset.UTC),
                amount, "EUR", payment.merchant(), payment.category());
    }

    private record RecurringPayment(String merchant, String category, String amount, int dayOfMonth, int everyMonths) {
    }
}

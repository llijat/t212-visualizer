package com.llijat.t212visualizer.service;

import com.llijat.t212visualizer.model.CardCashback;
import com.llijat.t212visualizer.model.CardExpense;
import com.llijat.t212visualizer.model.CustomCategory;
import com.llijat.t212visualizer.model.MerchantCategory;
import com.llijat.t212visualizer.repository.CardCashbackRepository;
import com.llijat.t212visualizer.repository.CardExpenseRepository;
import com.llijat.t212visualizer.repository.CustomCategoryRepository;
import com.llijat.t212visualizer.repository.MerchantCategoryRepository;
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

    // Same price every month, so the expenses page can detect them as subscriptions.
    // The last one costs more in the current month, to show a price rise.
    private static final List<DemoSubscription> SUBSCRIPTIONS = List.of(
            new DemoSubscription("Hooli Music", "ENTERTAINMENT", "10.99", 3, "10.99"),
            new DemoSubscription("Pied Piper Cloud", "SERVICE_PROVIDERS", "2.99", 8, "2.99"),
            new DemoSubscription("Globo Gym", "PERSONAL_SERVICES", "29.90", 26, "29.90"),
            new DemoSubscription("Wonka Streaming", "ENTERTAINMENT", "12.99", 1, "13.99"));

    // Like the real card: a small share of each payment comes back the following night
    private static final BigDecimal CASHBACK_RATE = new BigDecimal("0.015");

    // Two categories of your own, each with a merchant moved into it, so the demo shows them too
    private static final List<String> CUSTOM_CATEGORIES = List.of("Groceries", "Gym & Padel");
    private static final List<List<String>> MERCHANTS_MOVED = List.of(
            List.of("Acme Market", "Groceries"),
            List.of("Globo Gym", "Gym & Padel"));

    private final CardExpenseRepository cardExpenseRepository;
    private final CardCashbackRepository cardCashbackRepository;
    private final CustomCategoryRepository customCategoryRepository;
    private final MerchantCategoryRepository merchantCategoryRepository;

    public DemoCardExpenseSeeder(CardExpenseRepository cardExpenseRepository, CardCashbackRepository cardCashbackRepository,
                                 CustomCategoryRepository customCategoryRepository, MerchantCategoryRepository merchantCategoryRepository) {
        this.cardExpenseRepository = cardExpenseRepository;
        this.cardCashbackRepository = cardCashbackRepository;
        this.customCategoryRepository = customCategoryRepository;
        this.merchantCategoryRepository = merchantCategoryRepository;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (cardExpenseRepository.count() == 0) {
            LocalDate today = LocalDate.now(ZoneOffset.UTC);
            List<CardExpense> expenses = buildExpenses(today);
            cardExpenseRepository.saveAll(expenses);
            cardCashbackRepository.saveAll(buildCashback(expenses, today));
            customCategoryRepository.saveAll(CUSTOM_CATEGORIES.stream().map(CustomCategory::new).toList());
            merchantCategoryRepository.saveAll(buildMerchantsMoved());
        }
    }

    List<MerchantCategory> buildMerchantsMoved() {
        List<MerchantCategory> moved = new ArrayList<>();
        for (List<String> merchantAndCategory : MERCHANTS_MOVED) {
            MerchantCategory correction = new MerchantCategory(merchantAndCategory.get(0));
            correction.changeCategory(merchantAndCategory.get(1));
            moved.add(correction);
        }
        return moved;
    }

    /** 1.5 % of every payment, paid at 01:10 UTC the next day; nothing that would arrive after today. */
    List<CardCashback> buildCashback(List<CardExpense> expenses, LocalDate today) {
        List<CardCashback> cashback = new ArrayList<>();
        for (CardExpense expense : expenses) {
            LocalDate paidOn = expense.getDateTime().atZone(ZoneOffset.UTC).toLocalDate().plusDays(1);
            BigDecimal amount = expense.getAmount().negate().multiply(CASHBACK_RATE).setScale(2, RoundingMode.HALF_UP);
            if (!paidOn.isAfter(today) && amount.signum() > 0) {
                cashback.add(new CardCashback("demo-cashback-" + (cashback.size() + 1),
                        paidOn.atTime(1, 10).toInstant(ZoneOffset.UTC), amount, "EUR"));
            }
        }
        return cashback;
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
            for (DemoSubscription subscription : SUBSCRIPTIONS) {
                LocalDate date = month.atDay(subscription.dayOfMonth());
                if (!date.isAfter(today)) {
                    String amount = i == MONTHS - 1 ? subscription.currentAmount() : subscription.amount();
                    expenses.add(new CardExpense("demo-expense-" + (expenses.size() + 1), "Card debit",
                            date.atTime(12, 0).toInstant(ZoneOffset.UTC), new BigDecimal(amount).negate(), "EUR",
                            subscription.merchant(), subscription.category()));
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

    private record DemoSubscription(String merchant, String category, String amount, int dayOfMonth, String currentAmount) {
    }
}

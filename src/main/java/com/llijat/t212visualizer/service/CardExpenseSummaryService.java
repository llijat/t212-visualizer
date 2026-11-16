package com.llijat.t212visualizer.service;

import com.llijat.t212visualizer.dto.*;
import com.llijat.t212visualizer.model.CardCashback;
import com.llijat.t212visualizer.model.CardExpense;
import com.llijat.t212visualizer.repository.CardCashbackRepository;
import com.llijat.t212visualizer.repository.CardExpenseRepository;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.Month;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.*;

@Service
public class CardExpenseSummaryService {
    private static final ZoneId SPAIN = ZoneId.of("Europe/Madrid");
    private static final int TYPICAL_MONTHS = 3;

    private final CardExpenseRepository cardExpenseRepository;
    private final CardCashbackRepository cardCashbackRepository;

    public CardExpenseSummaryService(CardExpenseRepository cardExpenseRepository, CardCashbackRepository cardCashbackRepository) {
        this.cardExpenseRepository = cardExpenseRepository;
        this.cardCashbackRepository = cardCashbackRepository;
    }

    public CardExpenseSummary getSummary() {
        List<CardExpense> expenses = cardExpenseRepository.findAll();
        BigDecimal total = calculateTotal(expenses);
        List<MonthlySpending> months = calculateMonths(expenses);

        return new CardExpenseSummary(
                total,
                expenses.size(),
                findFirstPayment(expenses),
                findLastPayment(expenses),
                calculateMonthlyAverage(total, months),
                findBiggestMonth(months),
                months,
                calculateCategories(expenses),
                calculateTopMerchants(expenses));
    }

    private BigDecimal spentOn(CardExpense expense) {
        return expense.getAmount().negate();
    }

    private LocalDate dateOf(CardExpense expense) {
        return expense.getDateTime().atZone(SPAIN).toLocalDate();
    }

    private BigDecimal calculateTotal(List<CardExpense> expenses) {
        BigDecimal total = BigDecimal.ZERO;
        for (CardExpense expense : expenses) {
            total = total.add(spentOn(expense));
        }
        return total;
    }

    private LocalDate findFirstPayment(List<CardExpense> expenses) {
        LocalDate first = null;
        for (CardExpense expense : expenses) {
            LocalDate date = dateOf(expense);
            if (first == null || date.isBefore(first)) first = date;
        }
        return first;
    }

    private LocalDate findLastPayment(List<CardExpense> expenses) {
        LocalDate last = null;
        for (CardExpense expense : expenses) {
            LocalDate date = dateOf(expense);
            if (last == null || date.isAfter(last)) last = date;
        }
        return last;
    }

    private BigDecimal calculateMonthlyAverage(BigDecimal total, List<MonthlySpending> months) {
        if (months.isEmpty()) return BigDecimal.ZERO;
        return total.divide(BigDecimal.valueOf(months.size()), 2, RoundingMode.HALF_UP);
    }

    private BigDecimal findBiggestMonth(List<MonthlySpending> months) {
        BigDecimal biggest = BigDecimal.ZERO;
        for (MonthlySpending month : months) {
            biggest = biggest.max(month.total());
        }
        return biggest;
    }

    private List<MonthlySpending> calculateMonths(List<CardExpense> expenses) {
        List<MonthlySpending> months = new ArrayList<>();
        if (expenses.isEmpty()) return months;

        TreeMap<YearMonth, BigDecimal> totals = new TreeMap<>();
        for (CardExpense expense : expenses) {
            YearMonth month = YearMonth.from(dateOf(expense));
            totals.merge(month, spentOn(expense), BigDecimal::add);
        }

        YearMonth month = totals.firstKey();
        while (!month.isAfter(totals.lastKey())) {
            months.add(new MonthlySpending(month, totals.getOrDefault(month,BigDecimal.ZERO)));
            month = month.plusMonths(1);
        }
        return months;
    }

    private List<CategorySpending> calculateCategories(List<CardExpense> expenses) {
        Map<String, BigDecimal> totals = new HashMap<>();
        Map<String, Long> payments = new HashMap<>();

        for (CardExpense expense : expenses) {
            String category = expense.getMerchantCategory();
            if (category == null || category.isBlank()) category = "OTHER";
            totals.merge(category,spentOn(expense), BigDecimal::add);
            payments.merge(category, 1L, Long::sum);
        }

        List<CategorySpending> categories = new ArrayList<>();
        for (String category : totals.keySet()) {
            categories.add(new CategorySpending(category, totals.get(category),payments.get(category)));
        }
        categories.sort(Comparator.comparing(CategorySpending::total).reversed());
        return categories;
    }

    private List<MerchantSpending> calculateTopMerchants(List<CardExpense> expenses) {
        Map<String, BigDecimal> totals = new HashMap<>();
        Map<String, Long> payments = new HashMap<>();

        for (CardExpense expense : expenses) {
            String merchant = expense.getMerchantName();
            if (merchant == null || merchant.isBlank()) merchant = "Unknown merchant";
            totals.merge(merchant,spentOn(expense), BigDecimal::add);
            payments.merge(merchant, 1L, Long::sum);
        }

        List<MerchantSpending> merchants = new ArrayList<>();
        for (String merchant : totals.keySet()) {
            merchants.add(new MerchantSpending(merchant, totals.get(merchant),payments.get(merchant)));
        }
        merchants.sort(Comparator.comparing(MerchantSpending::total).reversed());
        return merchants.stream().limit(10).toList();
    }


    public MonthReport getMonthReport(YearMonth month, LocalDate today) {
        List<CardExpense> expenses = cardExpenseRepository.findAll();
        boolean current = month.equals(YearMonth.from(today));

        int lastDay = current ? today.getDayOfMonth() : month.lengthOfMonth();
        YearMonth previousMonth = month.minusMonths(1);
        int previousLastDay = current ? Math.min(lastDay, previousMonth.lengthOfMonth()) : previousMonth.lengthOfMonth();

        List<CardExpense> inMonth = between(expenses, month.atDay(1), month.atDay(lastDay));
        List<CardExpense> inPrevious = between(expenses, previousMonth.atDay(1),previousMonth.atDay(previousLastDay));

        BigDecimal spent = calculateTotal(inMonth);
        BigDecimal projected = current ? project(expenses, spent, month, today) : null;

        return new MonthReport(
                month,
                current,
                spent,
                calculateTotal(inPrevious),
                projected,
                calculateCategories(inMonth),
                calculateTopMerchants(inMonth),
                calculateDays(inMonth,month,lastDay));
    }

    private List<CardExpense> between(List<CardExpense> expenses, LocalDate from, LocalDate to) {
        List<CardExpense> result = new ArrayList<>();
        for (CardExpense expense : expenses) {
            LocalDate date = dateOf(expense);
            if (!date.isBefore(from) && !date.isAfter(to)) {
                result.add(expense);
            }
        }
        return result;
    }

    private BigDecimal project(List<CardExpense> expenses, BigDecimal spent, YearMonth month, LocalDate today) {
        LocalDate typicalFrom = month.minusMonths(TYPICAL_MONTHS).atDay(1);
        LocalDate typicalTo = month.atDay(1).minusDays(1);
        BigDecimal typicalTotal = calculateTotal(between(expenses, typicalFrom, typicalTo));
        long typicalDays = ChronoUnit.DAYS.between(typicalFrom, typicalTo) + 1;
        BigDecimal perDay = typicalTotal.divide(BigDecimal.valueOf(typicalDays), 2, RoundingMode.HALF_UP);

        int daysLeft = month.lengthOfMonth() - today.getDayOfMonth();
        return spent.add(perDay.multiply(BigDecimal.valueOf(daysLeft)));
    }

    public List<Subscription> getSubscriptions(LocalDate today) {
        return SubscriptionDetector.detect(cardExpenseRepository.findAll(), today);
    }

    private List<DailySpending> calculateDays(List<CardExpense> inMonth, YearMonth month, int lastDay) {
        Map<LocalDate, BigDecimal> totals = new HashMap<>();
        for (CardExpense expense : inMonth) {
            totals.merge(dateOf(expense), spentOn(expense), BigDecimal::add);
        }

        List<DailySpending> days = new ArrayList<>();
        for (int day = 1; day <= lastDay; ++day) {
            LocalDate date = month.atDay(day);
            days.add(new DailySpending(date, totals.getOrDefault(date, BigDecimal.ZERO)));
        }
        return days;
    }

    public BigDecimal getTotalCashback() {
        return cardCashbackRepository.findAll().stream()
                .map(CardCashback::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    public BigDecimal getCashback(YearMonth month) {
        return cardCashbackRepository.findAll().stream()
                .filter(cashback -> YearMonth.from(cashback.getDateTime().atZone(SPAIN)).equals(month))
                        .map(CardCashback::getAmount)
                        .reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}

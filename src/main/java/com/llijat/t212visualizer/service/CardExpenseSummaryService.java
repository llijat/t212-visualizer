package com.llijat.t212visualizer.service;

import com.llijat.t212visualizer.dto.CardExpenseSummary;
import com.llijat.t212visualizer.dto.CategorySpending;
import com.llijat.t212visualizer.dto.MerchantSpending;
import com.llijat.t212visualizer.dto.MonthlySpending;
import com.llijat.t212visualizer.model.CardExpense;
import com.llijat.t212visualizer.repository.CardExpenseRepository;
import jakarta.validation.constraints.Null;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.Month;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.*;

@Service
public class CardExpenseSummaryService {
    private static final ZoneId SPAIN = ZoneId.of("Europe/Madrid");

    private final CardExpenseRepository cardExpenseRepository;

    public CardExpenseSummaryService(CardExpenseRepository cardExpenseRepository) {
        this.cardExpenseRepository = cardExpenseRepository;
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
}

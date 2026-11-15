package com.llijat.t212visualizer.service;

import com.llijat.t212visualizer.dto.BudgetProgress;
import com.llijat.t212visualizer.dto.CategorySpending;
import com.llijat.t212visualizer.dto.MonthReport;
import com.llijat.t212visualizer.model.Budget;
import com.llijat.t212visualizer.repository.BudgetRepository;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class BudgetService {
    private final BudgetRepository budgetRepository;

    public BudgetService(BudgetRepository budgetRepository) {
        this.budgetRepository = budgetRepository;
    }

    public void saveBudget(String category, BigDecimal monthlyLimit) {
        if (monthlyLimit == null || monthlyLimit.signum() <= 0) throw new IllegalArgumentException("The monthly limit must be more than 0.");
        if (category == null || category.isBlank() || category.length() > 100) throw new IllegalArgumentException("Choose a category.");


        Budget budget = budgetRepository.findById(category).orElseGet(() -> new Budget(category));
        budget.updateLimit(monthlyLimit);
        budgetRepository.save(budget);
    }

    public void deleteBudget(String category) {
        budgetRepository.deleteById(category);
    }

    public List<BudgetProgress> getProgress(MonthReport report) {
        Map<String, BigDecimal> spentByCategory = report.categories().stream()
                .collect(Collectors.toMap(CategorySpending::category, CategorySpending::total));

        return budgetRepository.findAll().stream()
                .map(budget -> new BudgetProgress(
                        budget.getCategory(),
                        budget.getMonthlyLimit(),
                        spentByCategory.getOrDefault(budget.getCategory(), BigDecimal.ZERO)))
                .sorted(Comparator.comparing(BudgetProgress::category))
                .toList();
    }
}

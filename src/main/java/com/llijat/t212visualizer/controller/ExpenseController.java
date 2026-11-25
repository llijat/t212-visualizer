package com.llijat.t212visualizer.controller;

import com.llijat.t212visualizer.dto.MonthReport;
import com.llijat.t212visualizer.service.*;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;

@Controller
public class ExpenseController {
    private final CardExpenseImportService cardExpenseImportService;
    private final CardExpenseSummaryService cardExpenseSummaryService;
    private final BudgetService budgetService;
    private final MerchantCategoryService merchantCategoryService;
    private final Clock clock;
    private final CustomCategoryService customCategoryService;


    public ExpenseController(CardExpenseImportService cardExpenseImportService, CardExpenseSummaryService cardExpenseSummaryService, BudgetService budgetService, MerchantCategoryService merchantCategoryService, CustomCategoryService customCategoryService, Clock clock) {
        this.cardExpenseImportService = cardExpenseImportService;
        this.cardExpenseSummaryService = cardExpenseSummaryService;
        this.budgetService = budgetService;
        this.merchantCategoryService = merchantCategoryService;
        this.customCategoryService = customCategoryService;
        this.clock = clock;
    }

    @GetMapping("/expenses")
    public String expenses(@RequestParam(required = false) YearMonth month, Model model) {
        LocalDate today = LocalDate.now(clock);
        if (month == null) {
            month = YearMonth.from(today);
        }
        model.addAttribute("spending", cardExpenseSummaryService.getSummary());
        MonthReport report = cardExpenseSummaryService.getMonthReport(month, today);
        model.addAttribute("report", report);
        model.addAttribute("budgets", budgetService.getProgress(report));
        model.addAttribute("subscriptions", cardExpenseSummaryService.getSubscriptions(today));
        model.addAttribute("cashbackMonth", cardExpenseSummaryService.getCashback(month));
        model.addAttribute("cashbackTotal", cardExpenseSummaryService.getTotalCashback());
        model.addAttribute("categoryNames", customCategoryService.getCategoryNames());
        model.addAttribute("customCategories", customCategoryService.getAll());
        return "expenses";
    }

    @PostMapping("/expenses/import")
    public String importCsv(@RequestParam("file") MultipartFile file, RedirectAttributes redirect) {
        try (Reader reader = new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8)) {
            redirect.addFlashAttribute("importResult", cardExpenseImportService.importCsv(reader));
        }
        catch (IOException | IllegalArgumentException | DateTimeParseException e) {
            redirect.addFlashAttribute("importError", "This file could not be read. Export your history from Trading 212 as CSV and try again.");
        }

        return "redirect:/expenses";
    }

    @PostMapping("/expenses/budgets")
    public String saveBudget(@RequestParam String category,
                             @RequestParam BigDecimal monthlyLimit,
                             @RequestParam(required = false) YearMonth month,
                             RedirectAttributes redirect) {
        try {
            budgetService.saveBudget(category, monthlyLimit);
        } catch (IllegalArgumentException e) {
            redirect.addFlashAttribute("budgetError", e.getMessage());
        }
        return redirectToMonth(month);
    }

    @PostMapping("/expenses/budgets/delete")
    public String deleteBudget(@RequestParam String category,
                               @RequestParam(required = false) YearMonth month) {
        budgetService.deleteBudget(category);
        return redirectToMonth(month);
    }

    @PostMapping("/expenses/categories")
    public String saveCategory(@RequestParam String merchant,
                               @RequestParam String category,
                               @RequestParam(required = false) YearMonth month,
                               RedirectAttributes redirect) {
        try {
            merchantCategoryService.saveCategory(merchant, category);
        } catch (IllegalArgumentException e) {
            redirect.addFlashAttribute("categoryError", e.getMessage());
        }
        return redirectToMonth(month);
    }

    @PostMapping("/expenses/categories/reset")
    public String resetCategory(@RequestParam String merchant,
                                @RequestParam(required = false) YearMonth month) {
        merchantCategoryService.resetCategory(merchant);
        return redirectToMonth(month);
    }

    @PostMapping("/expenses/custom-categories")
    public String createCategory(@RequestParam String name,
                                 @RequestParam(required = false) String merchant,
                                 @RequestParam(required = false) YearMonth month,
                                 RedirectAttributes redirect) {
        try {
            String created = customCategoryService.create(name);
            if (merchant != null) merchantCategoryService.saveCategory(merchant, created);
        } catch (IllegalArgumentException e) {
            redirect.addFlashAttribute("categoryError", e.getMessage());
        }
        return redirectToMonth(month);
    }

    @PostMapping("/expenses/custom-categories/delete")
    public String deleteCategory(@RequestParam String name,
                                 @RequestParam(required = false) YearMonth month) {
        customCategoryService.delete(name);
        return redirectToMonth(month);
    }

    private String redirectToMonth(YearMonth month) {
        return month == null ? "redirect:/expenses" : "redirect:/expenses?month=" + month;
    }
}

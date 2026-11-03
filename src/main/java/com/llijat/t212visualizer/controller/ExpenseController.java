package com.llijat.t212visualizer.controller;

import com.llijat.t212visualizer.service.CardExpenseImportService;
import com.llijat.t212visualizer.service.CardExpenseSummaryService;
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
import java.nio.charset.StandardCharsets;
import java.time.format.DateTimeParseException;

@Controller
public class ExpenseController {
    private final CardExpenseImportService cardExpenseImportService;
    private final CardExpenseSummaryService cardExpenseSummaryService;

    public ExpenseController(CardExpenseImportService cardExpenseImportService, CardExpenseSummaryService cardExpenseSummaryService) {
        this.cardExpenseImportService = cardExpenseImportService;
        this.cardExpenseSummaryService = cardExpenseSummaryService;
    }

    @GetMapping("/expenses")
    public String expenses(Model model) {
        model.addAttribute("spending", cardExpenseSummaryService.getSummary());
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
}

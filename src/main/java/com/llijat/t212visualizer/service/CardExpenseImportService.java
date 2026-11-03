package com.llijat.t212visualizer.service;

import com.llijat.t212visualizer.dto.CardExpenseCsvResult;
import com.llijat.t212visualizer.dto.CardExpenseImportResult;
import com.llijat.t212visualizer.model.CardExpense;
import com.llijat.t212visualizer.repository.CardExpenseRepository;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.List;

@Service
public class CardExpenseImportService {
    private final CardExpenseCsvParser cardExpenseCsvParser;
    private final CardExpenseRepository cardExpenseRepository;

    public CardExpenseImportService(CardExpenseCsvParser cardExpenseCsvParser, CardExpenseRepository cardExpenseRepository) {
        this.cardExpenseCsvParser = cardExpenseCsvParser;
        this.cardExpenseRepository = cardExpenseRepository;
    }

    public CardExpenseImportResult importCsv(Reader reader) throws IOException {
        CardExpenseCsvResult parsed = cardExpenseCsvParser.parse(reader);
        List<CardExpense> newExpenses = new ArrayList<>();

        for (CardExpense expense : parsed.expenses()) {
            if (!cardExpenseRepository.existsById(expense.getReference())) newExpenses.add(expense);
        }

        cardExpenseRepository.saveAll(newExpenses);

        return new CardExpenseImportResult(newExpenses.size(), parsed.expenses().size() - newExpenses.size(), parsed.ignoredByAction());
    }
}

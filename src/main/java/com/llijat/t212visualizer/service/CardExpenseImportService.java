package com.llijat.t212visualizer.service;

import com.llijat.t212visualizer.dto.CardExpenseCsvResult;
import com.llijat.t212visualizer.dto.CardExpenseImportResult;
import com.llijat.t212visualizer.model.CardCashback;
import com.llijat.t212visualizer.model.CardExpense;
import com.llijat.t212visualizer.repository.CardCashbackRepository;
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
    private final CardCashbackRepository cardCashbackRepository;

    public CardExpenseImportService(CardExpenseCsvParser cardExpenseCsvParser, CardExpenseRepository cardExpenseRepository, CardCashbackRepository cardCashbackRepository) {
        this.cardExpenseCsvParser = cardExpenseCsvParser;
        this.cardExpenseRepository = cardExpenseRepository;
        this.cardCashbackRepository = cardCashbackRepository;
    }

    public CardExpenseImportResult importCsv(Reader reader) throws IOException {
        CardExpenseCsvResult parsed = cardExpenseCsvParser.parse(reader);
        List<CardExpense> newExpenses = new ArrayList<>();

        for (CardExpense expense : parsed.expenses()) {
            if (!cardExpenseRepository.existsById(expense.getReference())) newExpenses.add(expense);
        }

        cardExpenseRepository.saveAll(newExpenses);

        List<CardCashback> newCashback = new ArrayList<>();
        for (CardCashback c : parsed.cashback()) {
            if (!cardCashbackRepository.existsById(c.getReference())) newCashback.add(c);
        }
            cardCashbackRepository.saveAll(newCashback);

        return new CardExpenseImportResult(newExpenses.size(), parsed.expenses().size() - newExpenses.size(), newCashback.size(), parsed.ignoredByAction());
    }
}

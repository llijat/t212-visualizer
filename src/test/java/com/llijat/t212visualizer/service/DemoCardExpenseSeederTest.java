package com.llijat.t212visualizer.service;

import com.llijat.t212visualizer.model.CardExpense;
import com.llijat.t212visualizer.repository.CardExpenseRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.DefaultApplicationArguments;

import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DemoCardExpenseSeederTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 11, 15);

    @Mock
    private CardExpenseRepository repository;

    private DemoCardExpenseSeeder seeder;

    @BeforeEach
    void setUp() {
        seeder = new DemoCardExpenseSeeder(repository);
    }

    @Test
    void buildExpenses_coversTheLastTwelveMonths_withoutFuturePayments() {
        // Act
        List<CardExpense> expenses = seeder.buildExpenses(TODAY);

        // Assert
        Set<YearMonth> months = new HashSet<>();
        for (CardExpense expense : expenses) {
            months.add(YearMonth.from(expense.getDateTime().atZone(ZoneOffset.UTC)));
            assertFalse(expense.getDateTime().isAfter(Instant.parse("2026-11-15T23:59:59Z")));
        }
        assertEquals(12, months.size());
        assertTrue(months.contains(YearMonth.of(2025, 12)));
        assertTrue(months.contains(YearMonth.of(2026, 11)));
    }

    @Test
    void buildExpenses_looksLikeTheImportedPayments() {
        // Act
        List<CardExpense> expenses = seeder.buildExpenses(TODAY);

        // Assert: same action and sign as the real ones, and the summary needs a merchant and a category
        Set<String> references = new HashSet<>();
        for (CardExpense expense : expenses) {
            references.add(expense.getReference());
            assertEquals("Card debit", expense.getAction());
            assertEquals(-1, expense.getAmount().signum());
            assertFalse(expense.getMerchantName().isBlank());
            assertFalse(expense.getMerchantCategory().isBlank());
        }
        assertEquals(expenses.size(), references.size());
    }

    @Test
    void run_savesThePayments_whenTheTableIsEmpty() {
        // Arrange
        when(repository.count()).thenReturn(0L);

        // Act
        seeder.run(new DefaultApplicationArguments());

        // Assert
        verify(repository).saveAll(anyList());
    }

    @Test
    void run_doesNothing_whenThereAreAlreadyPayments() {
        // Arrange
        when(repository.count()).thenReturn(5L);

        // Act
        seeder.run(new DefaultApplicationArguments());

        // Assert
        verify(repository, never()).saveAll(any());
    }
}

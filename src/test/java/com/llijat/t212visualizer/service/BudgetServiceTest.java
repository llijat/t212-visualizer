package com.llijat.t212visualizer.service;

import com.llijat.t212visualizer.dto.BudgetProgress;
import com.llijat.t212visualizer.dto.CategorySpending;
import com.llijat.t212visualizer.dto.MonthReport;
import com.llijat.t212visualizer.model.Budget;
import com.llijat.t212visualizer.repository.BudgetRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BudgetServiceTest {

    @Mock
    private BudgetRepository repository;

    @Captor
    private ArgumentCaptor<Budget> savedCaptor;

    private BudgetService service;

    @BeforeEach
    void setUp() {
        service = new BudgetService(repository);
    }

    private static Budget budget(String category, String limit) {
        Budget budget = new Budget(category);
        budget.updateLimit(new BigDecimal(limit));
        return budget;
    }

    private static MonthReport monthWith(CategorySpending... categories) {
        return new MonthReport(YearMonth.of(2026, 11), true, BigDecimal.ZERO, BigDecimal.ZERO, null,
                List.of(categories), List.of());
    }

    // ===== Saving =====

    @Test
    void aNewCategory_getsANewBudget() {
        when(repository.findById("RESTAURANTS")).thenReturn(Optional.empty());

        service.saveBudget("RESTAURANTS", new BigDecimal("200"));

        verify(repository).save(savedCaptor.capture());
        assertEquals("RESTAURANTS", savedCaptor.getValue().getCategory());
        assertEquals(new BigDecimal("200"), savedCaptor.getValue().getMonthlyLimit());
    }

    @Test
    void aCategoryWithABudget_changesItsLimit_inTheSameRow() {
        Budget existing = budget("RESTAURANTS", "200");
        when(repository.findById("RESTAURANTS")).thenReturn(Optional.of(existing));

        service.saveBudget("RESTAURANTS", new BigDecimal("150"));

        verify(repository).save(savedCaptor.capture());
        assertSame(existing, savedCaptor.getValue());
        assertEquals(new BigDecimal("150"), existing.getMonthlyLimit());
    }

    @Test
    void aLimitOfZero_negative_orMissing_isRejected_andNothingIsSaved() {
        assertThrows(IllegalArgumentException.class, () -> service.saveBudget("RESTAURANTS", BigDecimal.ZERO));
        assertThrows(IllegalArgumentException.class, () -> service.saveBudget("RESTAURANTS", new BigDecimal("-50")));
        assertThrows(IllegalArgumentException.class, () -> service.saveBudget("RESTAURANTS", null));

        verify(repository, never()).save(any());
    }

    @Test
    void aMissingOrTooLongCategory_isRejected() {
        assertThrows(IllegalArgumentException.class, () -> service.saveBudget("", new BigDecimal("50")));
        assertThrows(IllegalArgumentException.class, () -> service.saveBudget("   ", new BigDecimal("50")));
        assertThrows(IllegalArgumentException.class, () -> service.saveBudget(null, new BigDecimal("50")));
        assertThrows(IllegalArgumentException.class, () -> service.saveBudget("X".repeat(101), new BigDecimal("50")));

        verify(repository, never()).save(any());
    }

    @Test
    void theErrorMessage_isReadyToShowOnThePage() {
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> service.saveBudget("RESTAURANTS", new BigDecimal("-1")));

        assertEquals("The monthly limit must be more than 0.", error.getMessage());
    }

    // ===== Deleting =====

    @Test
    void deleting_removesTheBudgetOfThatCategory() {
        service.deleteBudget("RESTAURANTS");

        verify(repository).deleteById("RESTAURANTS");
    }

    // ===== Progress in a month =====

    @Test
    void eachBudget_isComparedWithWhatItsCategorySpentThatMonth() {
        when(repository.findAll()).thenReturn(List.of(budget("RESTAURANTS", "200"), budget("RETAIL_STORES", "250")));

        List<BudgetProgress> progress = service.getProgress(monthWith(
                new CategorySpending("RETAIL_STORES", new BigDecimal("281.00"), 6),
                new CategorySpending("RESTAURANTS", new BigDecimal("164.00"), 9),
                new CategorySpending("TRANSPORT", new BigDecimal("40.00"), 2)));   // no budget: not listed

        assertEquals(2, progress.size());
        assertEquals(new BudgetProgress("RESTAURANTS", new BigDecimal("200"), new BigDecimal("164.00")), progress.get(0));
        assertEquals(new BudgetProgress("RETAIL_STORES", new BigDecimal("250"), new BigDecimal("281.00")), progress.get(1));
    }

    @Test
    void aBudgetWhoseCategorySpentNothing_showsZero() {
        // The category is missing from the month, so getOrDefault gives 0
        when(repository.findAll()).thenReturn(List.of(budget("CLOTHING_STORES", "100")));

        List<BudgetProgress> progress = service.getProgress(monthWith(
                new CategorySpending("RESTAURANTS", new BigDecimal("164.00"), 9)));

        assertEquals(BigDecimal.ZERO, progress.get(0).spent());
        assertEquals(0, progress.get(0).percent());
    }

    @Test
    void withoutBudgets_thereIsNoProgress() {
        when(repository.findAll()).thenReturn(List.of());

        assertTrue(service.getProgress(monthWith()).isEmpty());
    }
}

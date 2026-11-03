package com.llijat.t212visualizer.service;

import com.llijat.t212visualizer.dto.CardExpenseSummary;
import com.llijat.t212visualizer.dto.CategorySpending;
import com.llijat.t212visualizer.dto.MerchantSpending;
import com.llijat.t212visualizer.dto.MonthlySpending;
import com.llijat.t212visualizer.model.CardExpense;
import com.llijat.t212visualizer.repository.CardExpenseRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CardExpenseSummaryServiceTest {

    @Mock
    private CardExpenseRepository repository;

    private CardExpenseSummaryService summaryService;

    @BeforeEach
    void setUp() {
        summaryService = new CardExpenseSummaryService(repository);
    }

    private CardExpense expense(String amount, String dateTime, String merchant, String category) {
        return new CardExpense(merchant + dateTime, "Card debit", Instant.parse(dateTime), new BigDecimal(amount),
                "EUR", merchant, category);
    }

    // compareTo because BigDecimal's equals treats 5.0 and 5.00 as different
    private void assertAmount(String expected, BigDecimal actual) {
        assertEquals(0, new BigDecimal(expected).compareTo(actual), "expected " + expected + " but was " + actual);
    }

    @Test
    void getSummary_addsUpTheSpending_asPositiveAmounts() {
        // Arrange: the database keeps payments as negative amounts, like T212
        when(repository.findAll()).thenReturn(List.of(
                expense("-10.50", "2026-03-20T10:00:00Z", "Market", "RETAIL_STORES"),
                expense("-4.25", "2026-03-05T10:00:00Z", "Bar", "RESTAURANTS")));

        // Act
        CardExpenseSummary summary = summaryService.getSummary();

        // Assert
        assertAmount("14.75", summary.total());
        assertEquals(2, summary.payments());
        assertEquals(LocalDate.of(2026, 3, 5), summary.firstPayment());
        assertEquals(LocalDate.of(2026, 3, 20), summary.lastPayment());
    }

    @Test
    void getSummary_listsTheMonthsInOrder_includingThoseWithoutPayments() {
        // Arrange: nothing in February
        when(repository.findAll()).thenReturn(List.of(
                expense("-30.00", "2026-03-10T10:00:00Z", "Market", "RETAIL_STORES"),
                expense("-70.00", "2026-01-10T10:00:00Z", "Market", "RETAIL_STORES")));

        // Act
        List<MonthlySpending> months = summaryService.getSummary().months();

        // Assert: a month without payments must still appear in the chart, at zero
        assertEquals(3, months.size());
        assertEquals(YearMonth.of(2026, 1), months.get(0).month());
        assertAmount("70", months.get(0).total());
        assertEquals(YearMonth.of(2026, 2), months.get(1).month());
        assertAmount("0", months.get(1).total());
        assertEquals(YearMonth.of(2026, 3), months.get(2).month());
        assertAmount("30", months.get(2).total());
    }

    @Test
    void getSummary_calculatesTheBiggestMonth_andTheAverage() {
        // Arrange: 100 € over 3 months, counting the empty one
        when(repository.findAll()).thenReturn(List.of(
                expense("-30.00", "2026-03-10T10:00:00Z", "Market", "RETAIL_STORES"),
                expense("-70.00", "2026-01-10T10:00:00Z", "Market", "RETAIL_STORES")));

        // Act
        CardExpenseSummary summary = summaryService.getSummary();

        // Assert
        assertAmount("70", summary.biggestMonth());
        assertAmount("33.33", summary.monthlyAverage());
    }

    @Test
    void getSummary_usesSpanishTime_toChooseTheMonth() {
        // Arrange: 23:30 UTC on 31 January is already 00:30 on 1 February in Spain
        when(repository.findAll()).thenReturn(List.of(
                expense("-5.00", "2026-01-31T23:30:00Z", "Bar", "RESTAURANTS")));

        // Act
        CardExpenseSummary summary = summaryService.getSummary();

        // Assert
        assertEquals(YearMonth.of(2026, 2), summary.months().get(0).month());
        assertEquals(LocalDate.of(2026, 2, 1), summary.firstPayment());
    }

    @Test
    void getSummary_groupsByCategory_fromHighestToLowest() {
        // Arrange
        when(repository.findAll()).thenReturn(List.of(
                expense("-5.00", "2026-03-01T10:00:00Z", "Bar", "RESTAURANTS"),
                expense("-20.00", "2026-03-02T10:00:00Z", "Market", "RETAIL_STORES"),
                expense("-7.00", "2026-03-03T10:00:00Z", "Grill", "RESTAURANTS")));

        // Act
        List<CategorySpending> categories = summaryService.getSummary().categories();

        // Assert
        assertEquals(2, categories.size());
        assertEquals("RETAIL_STORES", categories.get(0).category());
        assertAmount("20", categories.get(0).total());
        assertEquals(1, categories.get(0).payments());
        assertEquals("RESTAURANTS", categories.get(1).category());
        assertAmount("12", categories.get(1).total());
        assertEquals(2, categories.get(1).payments());
    }

    @Test
    void getSummary_keepsOnlyTheTenBiggestMerchants() {
        // Arrange: 12 merchants; "Merchant 1" spends 1 €, "Merchant 12" spends 12 €
        List<CardExpense> expenses = new ArrayList<>();
        for (int i = 1; i <= 12; i++) {
            expenses.add(expense("-" + i, "2026-03-01T10:00:00Z", "Merchant " + i, "RETAIL_STORES"));
        }
        when(repository.findAll()).thenReturn(expenses);

        // Act
        List<MerchantSpending> merchants = summaryService.getSummary().topMerchants();

        // Assert
        assertEquals(10, merchants.size());
        assertEquals("Merchant 12", merchants.get(0).name());
        assertAmount("12", merchants.get(0).total());
        assertEquals(1, merchants.get(0).payments());
        assertEquals("Merchant 3", merchants.get(9).name());
    }

    @Test
    void getSummary_namesTheMissingMerchantsAndCategories() {
        // Arrange: the database allows them to be empty; the page needs some text to show
        when(repository.findAll()).thenReturn(List.of(
                expense("-5.00", "2026-03-01T10:00:00Z", "", ""),
                expense("-6.00", "2026-03-02T10:00:00Z", null, null)));

        // Act
        CardExpenseSummary summary = summaryService.getSummary();

        // Assert: both end up in the same group
        assertEquals(1, summary.categories().size());
        assertEquals("OTHER", summary.categories().get(0).category());
        assertEquals(1, summary.topMerchants().size());
        assertEquals("Unknown merchant", summary.topMerchants().get(0).name());
    }

    @Test
    void getSummary_withoutPayments_returnsAnEmptySummary() {
        // Arrange
        when(repository.findAll()).thenReturn(List.of());

        // Act
        CardExpenseSummary summary = summaryService.getSummary();

        // Assert: no errors from dividing by zero or from looking for a first payment
        assertEquals(0, summary.payments());
        assertAmount("0", summary.total());
        assertTrue(summary.months().isEmpty());
        assertTrue(summary.categories().isEmpty());
        assertTrue(summary.topMerchants().isEmpty());
    }
}

package com.llijat.t212visualizer.service;

import com.llijat.t212visualizer.dto.CardExpenseSummary;
import com.llijat.t212visualizer.dto.CategorySpending;
import com.llijat.t212visualizer.dto.MerchantSpending;
import com.llijat.t212visualizer.dto.MonthReport;
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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
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

    // ===== getMonthReport: one month, compared with the one before =====

    private static final LocalDate NOV_15 = LocalDate.of(2026, 11, 15);

    @Test
    void theRunningMonth_countsUpToToday_andComparesWithTheSameDaysOfTheMonthBefore() {
        when(repository.findAll()).thenReturn(List.of(
                expense("-100.00", "2026-11-03T12:00:00Z", "Market", "RETAIL_STORES"),
                expense("-60.00", "2026-11-15T12:00:00Z", "Bar", "RESTAURANTS"),
                expense("-80.00", "2026-10-10T12:00:00Z", "Market", "RETAIL_STORES"),
                expense("-500.00", "2026-10-20T12:00:00Z", "Shop", "RETAIL_STORES")));  // after day 15: not compared

        MonthReport report = summaryService.getMonthReport(YearMonth.of(2026, 11), NOV_15);

        assertTrue(report.current());
        assertAmount("160.00", report.spent());
        assertAmount("80.00", report.previousSameDays());
        assertAmount("100.0", report.changePercent());
    }

    @Test
    void aPastMonth_isComparedWithTheWholeMonthBefore_andHasNoProjection() {
        when(repository.findAll()).thenReturn(List.of(
                expense("-90.00", "2026-09-30T12:00:00Z", "Market", "RETAIL_STORES"),
                expense("-80.00", "2026-08-31T12:00:00Z", "Market", "RETAIL_STORES"),
                expense("-20.00", "2026-08-02T12:00:00Z", "Bar", "RESTAURANTS")));

        MonthReport report = summaryService.getMonthReport(YearMonth.of(2026, 9), NOV_15);

        assertFalse(report.current());
        assertAmount("90.00", report.spent());
        assertAmount("100.00", report.previousSameDays());
        assertNull(report.projected());
    }

    @Test
    void on31March_februaryIsComparedUpToThe28th_withoutFailing() {
        // Without Math.min, February 31 would throw a DateTimeException
        when(repository.findAll()).thenReturn(List.of(
                expense("-40.00", "2026-03-31T12:00:00Z", "Market", "RETAIL_STORES"),
                expense("-25.00", "2026-02-28T12:00:00Z", "Market", "RETAIL_STORES")));

        MonthReport report = summaryService.getMonthReport(YearMonth.of(2026, 3), LocalDate.of(2026, 3, 31));

        assertAmount("40.00", report.spent());
        assertAmount("25.00", report.previousSameDays());
    }

    @Test
    void aPaymentJustAfterMidnightInSpain_belongsToTheSpanishDay() {
        // 31 October 23:30 UTC is already 1 November 00:30 in Madrid
        when(repository.findAll()).thenReturn(List.of(expense("-15.00", "2026-10-31T23:30:00Z", "Bar", "RESTAURANTS")));

        MonthReport report = summaryService.getMonthReport(YearMonth.of(2026, 11), NOV_15);

        assertAmount("15.00", report.spent());
        assertAmount("0", report.previousSameDays());
    }

    @Test
    void theProjection_addsTheUsualDailySpendingOfTheLast3FullMonths_forTheDaysLeft() {
        // August to October: 920 EUR in 92 days = 10 EUR a day.
        // On 15 November, with 200 spent and 15 days left: 200 + 15 * 10 = 350
        when(repository.findAll()).thenReturn(List.of(
                expense("-200.00", "2026-11-03T12:00:00Z", "Shop", "RETAIL_STORES"),
                expense("-300.00", "2026-08-01T12:00:00Z", "Market", "RETAIL_STORES"),
                expense("-300.00", "2026-09-15T12:00:00Z", "Market", "RETAIL_STORES"),
                expense("-320.00", "2026-10-31T12:00:00Z", "Market", "RETAIL_STORES"),
                expense("-1000.00", "2026-07-31T12:00:00Z", "Travel", "HOTELS")));  // before August: not counted

        MonthReport report = summaryService.getMonthReport(YearMonth.of(2026, 11), NOV_15);

        assertAmount("350.00", report.projected());
    }

    @Test
    void onTheLastDayOfTheMonth_theProjectionIsWhatWasSpent() {
        when(repository.findAll()).thenReturn(List.of(
                expense("-70.00", "2026-11-30T12:00:00Z", "Shop", "RETAIL_STORES"),
                expense("-920.00", "2026-09-15T12:00:00Z", "Market", "RETAIL_STORES")));

        MonthReport report = summaryService.getMonthReport(YearMonth.of(2026, 11), LocalDate.of(2026, 11, 30));

        assertAmount("70.00", report.projected());
    }

    @Test
    void categoriesAndMerchants_onlyIncludeThePaymentsOfThatMonth() {
        when(repository.findAll()).thenReturn(List.of(
                expense("-30.00", "2026-11-05T12:00:00Z", "Bar", "RESTAURANTS"),
                expense("-10.00", "2026-11-06T12:00:00Z", "Bar", "RESTAURANTS"),
                expense("-99.00", "2026-10-05T12:00:00Z", "Cinema", "ENTERTAINMENT")));

        MonthReport report = summaryService.getMonthReport(YearMonth.of(2026, 11), NOV_15);

        assertEquals(1, report.categories().size());
        assertEquals("RESTAURANTS", report.categories().get(0).category());
        assertAmount("40.00", report.categories().get(0).total());
        assertEquals(1, report.topMerchants().size());
        assertEquals("Bar", report.topMerchants().get(0).name());
    }

    @Test
    void withNoPaymentsAtAll_everythingIsZero_andThereIsNoChange() {
        when(repository.findAll()).thenReturn(List.of());

        MonthReport report = summaryService.getMonthReport(YearMonth.of(2026, 11), NOV_15);

        assertAmount("0", report.spent());
        assertAmount("0", report.previousSameDays());
        assertAmount("0", report.projected());
        assertNull(report.changePercent());
        assertTrue(report.categories().isEmpty());
    }
}

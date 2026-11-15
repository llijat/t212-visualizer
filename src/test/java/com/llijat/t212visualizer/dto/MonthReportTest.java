package com.llijat.t212visualizer.dto;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class MonthReportTest {

    private static MonthReport report(String spent, String previousSameDays) {
        return new MonthReport(YearMonth.of(2026, 11), true, new BigDecimal(spent), new BigDecimal(previousSameDays),
                null, List.of(), List.of(), List.of());
    }

    @Test
    void spendingMore_givesAPositiveChange() {
        // (660 - 600) * 100 / 600 = 10.0
        assertEquals(new BigDecimal("10.0"), report("660", "600").changePercent());
    }

    @Test
    void spendingLess_givesANegativeChange() {
        // (450 - 600) * 100 / 600 = -25.0
        assertEquals(new BigDecimal("-25.0"), report("450", "600").changePercent());
    }

    @Test
    void theSameAmount_givesZero() {
        assertEquals(new BigDecimal("0.0"), report("600", "600").changePercent());
    }

    @Test
    void theChange_isRoundedToOneDecimal() {
        // (100 - 300) * 100 / 300 = -66.666... -> -66.7
        assertEquals(new BigDecimal("-66.7"), report("100", "300").changePercent());
    }

    @Test
    void withNothingToCompareWith_thereIsNoChange() {
        // Going from 0 to 50 is not "5000 % more": there is simply no percentage
        assertNull(report("50", "0").changePercent());
        assertNull(report("0", "0.00").changePercent());
    }

    // ===== biggestDay: the scale of the heat map =====

    private static MonthReport reportWithDays(String... totals) {
        List<DailySpending> days = new java.util.ArrayList<>();
        for (int i = 0; i < totals.length; i++) {
            days.add(new DailySpending(java.time.LocalDate.of(2026, 11, i + 1), new BigDecimal(totals[i])));
        }
        return new MonthReport(YearMonth.of(2026, 11), true, BigDecimal.ZERO, BigDecimal.ZERO, null,
                List.of(), List.of(), days);
    }

    @Test
    void theBiggestDay_isTheDayThatSpentMost() {
        assertEquals(new BigDecimal("62.40"), reportWithDays("0", "12.50", "62.40", "4.20").biggestDay());
    }

    @Test
    void aMonthWithoutPayments_hasZeroAsItsBiggestDay() {
        assertEquals(0, BigDecimal.ZERO.compareTo(reportWithDays("0", "0", "0").biggestDay()));
        assertEquals(BigDecimal.ZERO, reportWithDays().biggestDay());
    }
}

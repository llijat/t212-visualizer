package com.llijat.t212visualizer.dto;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DividendHistoryTest {

    private static MonthlyDividends month(int year, int month, String euros) {
        return new MonthlyDividends(YearMonth.of(year, month), new BigDecimal(euros));
    }

    private final DividendHistory history = new DividendHistory(List.of(
            month(2025, 12, "4.20"),
            month(2026, 1, "0"),
            month(2026, 3, "12.60"),
            month(2026, 6, "6.30")));

    @Test
    void total_addsEveryMonth() {
        assertEquals(new BigDecimal("23.10"), history.total());
    }

    @Test
    void totalIn_onlyAddsTheMonthsOfThatYear() {
        assertEquals(new BigDecimal("18.90"), history.totalIn(2026));
        assertEquals(new BigDecimal("4.20"), history.totalIn(2025));
    }

    @Test
    void theBestMonth_isTheTallestBar() {
        assertEquals(new BigDecimal("12.60"), history.best());
        assertEquals(100, history.heightOf(history.months().get(2)));
    }

    @Test
    void theOtherBars_areAShareOfTheBestMonth() {
        assertEquals(50, history.heightOf(history.months().get(3)));   // 6.30 of 12.60
        assertEquals(33, history.heightOf(history.months().get(0)));   // 4.20 of 12.60 = 33.3
        assertEquals(0, history.heightOf(history.months().get(1)));
    }

    @Test
    void withoutAnyDividend_everyBarIsZero_insteadOfDividingByZero() {
        DividendHistory empty = new DividendHistory(List.of(month(2026, 1, "0"), month(2026, 2, "0")));

        assertEquals(BigDecimal.ZERO, empty.total());
        assertEquals(0, empty.heightOf(empty.months().get(0)));
    }
}

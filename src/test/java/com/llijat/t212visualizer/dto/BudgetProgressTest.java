package com.llijat.t212visualizer.dto;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BudgetProgressTest {

    private static BudgetProgress progress(String limit, String spent) {
        return new BudgetProgress("RESTAURANTS", new BigDecimal(limit), new BigDecimal(spent));
    }

    @Test
    void underTheLimit_givesThePercentUsed() {
        assertEquals(82, progress("200", "164").percent());
        assertFalse(progress("200", "164").isOver());
    }

    @Test
    void exactlyTheLimit_isAHundredPercent_butNotOver() {
        assertEquals(100, progress("200", "200.00").percent());
        assertFalse(progress("200", "200.00").isOver());
    }

    @Test
    void overTheLimit_goesAboveAHundred() {
        assertEquals(125, progress("200", "250").percent());
        assertTrue(progress("200", "250").isOver());
    }

    @Test
    void oneCentOver_isAlreadyOver() {
        assertTrue(progress("200", "200.01").isOver());
    }

    @Test
    void thePercent_isRoundedToAWholeNumber() {
        // 100 / 300 = 33.33...% and 200 / 300 = 66.66...%
        assertEquals(33, progress("300", "100").percent());
        assertEquals(67, progress("300", "200").percent());
    }

    @Test
    void nothingSpent_isZeroPercent() {
        assertEquals(0, progress("80", "0").percent());
    }
}

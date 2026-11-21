package com.llijat.t212visualizer.dto;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/** Alpha Vantage sends everything as text, and "None" when it does not know a value. */
class AlphaVantageDividendDtoTest {

    @Test
    void theTexts_becomeDatesAndAnExactAmount() {
        AlphaVantageDividendDto dividend = new AlphaVantageDividendDto("2026-11-10", "2026-12-10", "1.69");

        assertEquals(LocalDate.of(2026, 11, 10), dividend.exDividendDay());
        assertEquals(LocalDate.of(2026, 12, 10), dividend.paymentDay());
        assertEquals(new BigDecimal("1.69"), dividend.amountPerShare());
    }

    @Test
    void smallAmounts_keepAllTheirDecimals() {
        AlphaVantageDividendDto dividend = new AlphaVantageDividendDto("2026-11-10", "2026-12-10", "0.0425");

        assertEquals(new BigDecimal("0.0425"), dividend.amountPerShare());
    }

    @Test
    void none_isAnUnknownValue_notAnError() {
        AlphaVantageDividendDto dividend = new AlphaVantageDividendDto("None", "None", "None");

        assertNull(dividend.exDividendDay());
        assertNull(dividend.paymentDay());
        assertNull(dividend.amountPerShare());
    }

    @Test
    void aMissingField_isAlsoUnknown() {
        AlphaVantageDividendDto dividend = new AlphaVantageDividendDto(null, null, null);

        assertNull(dividend.exDividendDay());
        assertNull(dividend.paymentDay());
        assertNull(dividend.amountPerShare());
    }
}

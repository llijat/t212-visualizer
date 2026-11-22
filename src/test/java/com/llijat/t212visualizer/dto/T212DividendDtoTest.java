package com.llijat.t212visualizer.dto;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** T212 sends paidOn with its time zone, for example 2026-10-16T09:30:00.000+02:00. */
class T212DividendDtoTest {

    private static T212DividendDto paidOn(String paidOn) {
        return new T212DividendDto("MU_US_EQ", new BigDecimal("8.5"), new BigDecimal("0.115"), new BigDecimal("0.93"), paidOn);
    }

    @Test
    void thePaymentDay_isTheDateInTheText() {
        assertEquals(LocalDate.of(2026, 10, 16), paidOn("2026-10-16T09:30:00.000+02:00").paidDay());
    }

    @Test
    void lateAtNight_itStaysOnTheDayT212Gives_insteadOfMovingToTheNextOneInUtc() {
        // 23:30 at +02:00 is already the 17th in UTC: the 16th is the day that matches Alpha Vantage's payment date
        assertEquals(LocalDate.of(2026, 10, 16), paidOn("2026-10-16T23:30:00.000+02:00").paidDay());
    }

    @Test
    void utcTimes_areReadToo() {
        assertEquals(LocalDate.of(2026, 10, 16), paidOn("2026-10-16T09:30:00.000Z").paidDay());
    }
}

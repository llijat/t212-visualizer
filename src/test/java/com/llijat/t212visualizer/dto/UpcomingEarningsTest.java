package com.llijat.t212visualizer.dto;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;

class UpcomingEarningsTest {

    private static UpcomingEarnings at(String hour) {
        return new UpcomingEarnings("NVDA", "NVDA_US_EQ", LocalDate.of(2026, 11, 19), hour);
    }

    @Test
    void finnhubsCodes_becomeWords() {
        assertEquals("before the open", at("bmo").hourLabel());
        assertEquals("after the close", at("amc").hourLabel());
        assertEquals("during the session", at("dmh").hourLabel());
    }

    @Test
    void anUnknownHour_saysNothing() {
        // Finnhub sends "" while the hour is not announced yet
        assertEquals("", at("").hourLabel());
        assertEquals("", at("xyz").hourLabel());
    }

    @Test
    void aMissingHour_saysNothing_insteadOfBreaking() {
        // A switch cannot look inside null: without the early return this would be a NullPointerException
        assertEquals("", at(null).hourLabel());
    }
}

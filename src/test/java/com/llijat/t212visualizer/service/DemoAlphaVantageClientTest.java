package com.llijat.t212visualizer.service;

import com.llijat.t212visualizer.dto.AlphaVantageDividendDto;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.DayOfWeek;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The demo calendar needs dividends to show: one coming soon, and a company that never pays. */
class DemoAlphaVantageClientTest {

    private final DemoAlphaVantageClient client = new DemoAlphaVantageClient();

    @Test
    void globex_hasAnAnnouncedDividend_inFourDays_andThreePastOnes() {
        List<AlphaVantageDividendDto> dividends = client.fetchDividends("GLBX");
        LocalDate today = LocalDate.now();

        assertEquals(4, dividends.size());
        assertEquals(today.plusDays(4), dividends.get(0).paymentDay());
        assertEquals(today.plusDays(4).minusDays(14), dividends.get(0).exDividendDay());
        assertEquals(today.plusDays(4).minusMonths(9), dividends.get(3).paymentDay());
    }

    @Test
    void hooli_neverPays() {
        assertTrue(client.fetchDividends("HOOL").isEmpty());
    }

    @Test
    void everyCompany_alwaysGetsTheSameDividends() {
        assertEquals(client.fetchDividends("ACME"), client.fetchDividends("ACME"));
    }

    // ===== Daily closes, for the mini charts =====

    @Test
    void theCloses_are100WorkingDays_endingYesterday() {
        Map<LocalDate, BigDecimal> closes = client.fetchDailyCloses("ACME");

        assertEquals(100, closes.size());
        assertFalse(closes.containsKey(LocalDate.now()));   // today's price comes from Finnhub
        closes.keySet().forEach(day -> assertTrue(day.getDayOfWeek().getValue() < DayOfWeek.SATURDAY.getValue(), day + " is a weekend"));
    }

    @Test
    void theLastClose_is100_likeTodaysDemoPrice_soTheLineDoesNotJump() {
        LocalDate lastDay = client.fetchDailyCloses("ACME").keySet().stream().max(LocalDate::compareTo).orElseThrow();

        assertEquals(new BigDecimal("100.0000"), client.fetchDailyCloses("ACME").get(lastDay));
    }

    @Test
    void eachCompany_hasItsOwnWalk_andAlwaysTheSameOne() {
        assertEquals(client.fetchDailyCloses("ACME"), client.fetchDailyCloses("ACME"));
        assertNotEquals(client.fetchDailyCloses("ACME"), client.fetchDailyCloses("GLBX"));
    }
}

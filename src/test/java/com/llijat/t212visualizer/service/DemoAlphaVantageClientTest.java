package com.llijat.t212visualizer.service;

import com.llijat.t212visualizer.dto.AlphaVantageDividendDto;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
}

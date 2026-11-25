package com.llijat.t212visualizer.model;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class EarningsEventTest {

    @Test
    void newEvent_hasOnlyItsIdentity() {
        EarningsEvent event = new EarningsEvent("MU_US_EQ", 2026, 1);

        assertNull(event.getId());
        assertEquals("MU_US_EQ", event.getTicker());
        assertEquals(2026, event.getFiscalYear());
        assertEquals(1, event.getFiscalQuarter());
        assertNull(event.getReportDate());
        assertNull(event.getEpsActual());
    }

    @Test
    void updateReport_copiesAllSixValues() {
        EarningsEvent event = new EarningsEvent("MU_US_EQ", 2026, 1);

        event.updateReport(LocalDate.of(2026, 12, 17), "amc",
                new BigDecimal("1.65"), new BigDecimal("1.79"),
                new BigDecimal("8700000000"), new BigDecimal("8900000000"));

        assertEquals(LocalDate.of(2026, 12, 17), event.getReportDate());
        assertEquals("amc", event.getReportHour());
        assertEquals(new BigDecimal("1.65"), event.getEpsEstimate());
        assertEquals(new BigDecimal("1.79"), event.getEpsActual());
        assertEquals(new BigDecimal("8700000000"), event.getRevenueEstimate());
        assertEquals(new BigDecimal("8900000000"), event.getRevenueActual());
    }

    @Test
    void updateReport_again_replacesThePreviousValues() {
        EarningsEvent event = new EarningsEvent("MU_US_EQ", 2026, 1);
        event.updateReport(LocalDate.of(2026, 12, 17), "amc",
                new BigDecimal("1.65"), null, new BigDecimal("8700000000"), null);

        // The company moves the date and then publishes
        event.updateReport(LocalDate.of(2026, 12, 18), "bmo",
                new BigDecimal("1.70"), new BigDecimal("1.79"),
                new BigDecimal("8750000000"), new BigDecimal("8900000000"));

        assertEquals(LocalDate.of(2026, 12, 18), event.getReportDate());
        assertEquals("bmo", event.getReportHour());
        assertEquals(new BigDecimal("1.70"), event.getEpsEstimate());
        assertEquals(new BigDecimal("1.79"), event.getEpsActual());
        assertEquals(new BigDecimal("8750000000"), event.getRevenueEstimate());
        assertEquals(new BigDecimal("8900000000"), event.getRevenueActual());
    }

    @Test
    void updateYoy_leavesTheReportUntouched() {
        EarningsEvent event = new EarningsEvent("MU_US_EQ", 2026, 1);
        event.updateReport(LocalDate.of(2026, 12, 17), "amc",
                new BigDecimal("1.65"), new BigDecimal("1.79"),
                new BigDecimal("8700000000"), new BigDecimal("8900000000"));

        event.updateYoy(new BigDecimal("12.5"), new BigDecimal("-3.2"));

        assertEquals(new BigDecimal("12.5"), event.getEpsYoy());
        assertEquals(new BigDecimal("-3.2"), event.getRevenueYoy());
        assertEquals(new BigDecimal("1.79"), event.getEpsActual());
        assertEquals(LocalDate.of(2026, 12, 17), event.getReportDate());
    }
}

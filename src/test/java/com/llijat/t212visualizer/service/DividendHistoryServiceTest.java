package com.llijat.t212visualizer.service;

import java.time.Clock;
import java.time.Instant;

import com.llijat.t212visualizer.dto.DividendHistory;
import com.llijat.t212visualizer.dto.DividendPayer;
import com.llijat.t212visualizer.dto.MonthlyDividends;
import com.llijat.t212visualizer.model.DividendEvent;
import com.llijat.t212visualizer.repository.DividendEventRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DividendHistoryServiceTest {

    // "Now" for the code under test: fixed, so a test run at midnight does not see two different days
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-11-15T11:00:00Z"), ZoneId.of("Europe/Madrid"));

    private static final YearMonth NOW = YearMonth.now(CLOCK);

    @Mock
    private DividendEventRepository repository;

    private DividendHistoryService service;

    @BeforeEach
    void setUp() {
        service = new DividendHistoryService(repository, CLOCK);
    }

    private static DividendEvent paid(String ticker, LocalDate day, String euros) {
        DividendEvent dividend = new DividendEvent(ticker, day);
        dividend.recordPayment(new BigDecimal(euros), new BigDecimal("0.10"), BigDecimal.TEN);
        return dividend;
    }

    // The repository is asked for the 1st of the month 11 months ago up to the last day of this month
    private void theTableHolds(DividendEvent... received) {
        when(repository.findByReceivedEurIsNotNullAndPaymentDateBetween(NOW.minusMonths(11).atDay(1), NOW.atEndOfMonth()))
                .thenReturn(List.of(received));
    }

    @Test
    void thereAreAlways12Months_fromTheOldestToThisOne() {
        theTableHolds();

        List<YearMonth> months = service.getLast12Months().months().stream().map(MonthlyDividends::month).toList();

        assertEquals(12, months.size());
        assertEquals(NOW.minusMonths(11), months.get(0));
        assertEquals(NOW, months.get(11));
    }

    @Test
    void monthsWithoutDividends_areZero_soTheGapsShow() {
        theTableHolds(paid("MU_US_EQ", NOW.minusMonths(2).atDay(15), "0.84"));

        DividendHistory history = service.getLast12Months();

        assertEquals(BigDecimal.ZERO, history.months().get(0).receivedEur());
        assertEquals(new BigDecimal("0.84"), history.months().get(9).receivedEur());
    }

    @Test
    void paymentsOfTheSameMonth_areAddedUp_whateverTheStock() {
        theTableHolds(
                paid("MU_US_EQ", NOW.atDay(3), "0.84"),
                paid("ORCL_US_EQ", NOW.atDay(20), "3.65"),
                paid("MU_US_EQ", NOW.minusMonths(3).atDay(3), "0.84"));

        DividendHistory history = service.getLast12Months();

        assertEquals(new BigDecimal("4.49"), history.months().get(11).receivedEur());
        assertEquals(new BigDecimal("0.84"), history.months().get(8).receivedEur());
        assertEquals(new BigDecimal("5.33"), history.total());
    }

    // ===== Who paid each month =====

    @Test
    void eachMonth_saysWhoPaid_theBiggestFirst() {
        theTableHolds(
                paid("O_US_EQ", NOW.atDay(15), "0.30"),
                paid("VICI_US_EQ", NOW.atDay(8), "0.42"));

        List<DividendPayer> payers = service.getLast12Months().months().get(11).payers();

        assertEquals(List.of(
                new DividendPayer("VICI_US_EQ", new BigDecimal("0.42")),
                new DividendPayer("O_US_EQ", new BigDecimal("0.30"))), payers);
    }

    @Test
    void twoPaymentsOfTheSameStockInAMonth_areOnePayer_withBothAddedUp() {
        // The ordinary and the extraordinary dividend, on different days of the same month
        theTableHolds(
                paid("MU_US_EQ", NOW.atDay(3), "0.84"),
                paid("MU_US_EQ", NOW.atDay(24), "1.50"));

        MonthlyDividends month = service.getLast12Months().months().get(11);

        assertEquals(List.of(new DividendPayer("MU_US_EQ", new BigDecimal("2.34"))), month.payers());
        assertEquals(new BigDecimal("2.34"), month.receivedEur());
    }

    @Test
    void theMonthTotal_isTheSumOfItsPayers() {
        theTableHolds(
                paid("O_US_EQ", NOW.minusMonths(1).atDay(15), "0.30"),
                paid("VICI_US_EQ", NOW.minusMonths(1).atDay(8), "0.42"));

        assertEquals(new BigDecimal("0.72"), service.getLast12Months().months().get(10).receivedEur());
    }

    @Test
    void aMonthWithoutDividends_hasNoPayers() {
        theTableHolds();

        MonthlyDividends month = service.getLast12Months().months().get(0);

        assertEquals(List.of(), month.payers());
        assertEquals(BigDecimal.ZERO, month.receivedEur());
    }
}

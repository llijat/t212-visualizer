package com.llijat.t212visualizer.service;

import com.llijat.t212visualizer.dto.DividendHistory;
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

    private static final YearMonth NOW = YearMonth.now(ZoneId.of("Europe/Madrid"));

    @Mock
    private DividendEventRepository repository;

    private DividendHistoryService service;

    @BeforeEach
    void setUp() {
        service = new DividendHistoryService(repository);
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
}

package com.llijat.t212visualizer.service;

import com.llijat.t212visualizer.dto.T212DividendDto;
import com.llijat.t212visualizer.dto.T212DividendPageDto;
import com.llijat.t212visualizer.model.DividendEvent;
import com.llijat.t212visualizer.repository.DividendEventRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DividendPaymentServiceTest {

    private static final String FIRST_PAGE = "/equity/history/dividends?limit=50";

    @Mock
    private T212Client t212Client;

    @Mock
    private DividendEventRepository repository;

    @Captor
    private ArgumentCaptor<DividendEvent> savedCaptor;

    private DividendPaymentService service;

    @BeforeEach
    void setUp() {
        // No waiting between pages in the tests
        service = new DividendPaymentService(t212Client, repository, 0);
    }

    private static T212DividendDto paid(String ticker, int daysAgo, String perShare, String inEuro) {
        LocalDate day = LocalDate.now().minusDays(daysAgo);
        return new T212DividendDto(ticker, perShare == null ? null : new BigDecimal(perShare),
                inEuro == null ? null : new BigDecimal(inEuro), day + "T09:30:00.000+01:00");
    }

    private static T212DividendDto paidMonthsAgo(String ticker, int months) {
        LocalDate day = LocalDate.now().minusMonths(months);
        return new T212DividendDto(ticker, new BigDecimal("0.115"), new BigDecimal("0.84"), day + "T09:30:00.000+01:00");
    }

    private void onePage(T212DividendDto... payments) {
        when(t212Client.fetchDividends(FIRST_PAGE)).thenReturn(new T212DividendPageDto(List.of(payments), null));
    }

    private void nothingSavedYet() {
        when(repository.findByTickerAndPaymentDate(anyString(), any())).thenReturn(Optional.empty());
    }

    // ===== Saving =====

    @Test
    void aPaymentWithoutAnAnnouncedRow_createsOne_withWhatT212Says() {
        // A European stock, or a payment older than what Alpha Vantage keeps
        onePage(paid("RWEd_EQ", 20, "1.10", "8.80"));
        nothingSavedYet();

        service.importPayments();

        verify(repository).save(savedCaptor.capture());
        DividendEvent saved = savedCaptor.getValue();
        assertEquals("RWEd_EQ", saved.getTicker());
        assertEquals(LocalDate.now().minusDays(20), saved.getPaymentDate());
        assertEquals(new BigDecimal("8.80"), saved.getReceivedEur());
        assertEquals(new BigDecimal("1.10"), saved.getAmountPerShare());
        assertNull(saved.getExDividendDate());
    }

    @Test
    void aPaymentOfAnAnnouncedDividend_fillsThatRow_andKeepsAlphaVantagesAmount() {
        DividendEvent announced = new DividendEvent("MU_US_EQ", LocalDate.now().minusDays(5));
        announced.updateAnnouncement(LocalDate.now().minusDays(19), new BigDecimal("0.115"));
        onePage(paid("MU_US_EQ", 5, "0.1150", "0.84"));
        when(repository.findByTickerAndPaymentDate("MU_US_EQ", LocalDate.now().minusDays(5))).thenReturn(Optional.of(announced));

        service.importPayments();

        verify(repository).save(savedCaptor.capture());
        assertSame(announced, savedCaptor.getValue());
        assertEquals(new BigDecimal("0.84"), announced.getReceivedEur());
        assertEquals(new BigDecimal("0.115"), announced.getAmountPerShare());
        assertEquals(LocalDate.now().minusDays(19), announced.getExDividendDate());
    }

    @Test
    void twoPaymentsOfTheSameStockOnTheSameDay_areAddedUp_intoOneRow() {
        // An ordinary and a special dividend paid together
        onePage(paid("MU_US_EQ", 5, "0.115", "0.84"), paid("MU_US_EQ", 5, "0.50", "3.65"));
        nothingSavedYet();

        service.importPayments();

        verify(repository, times(1)).save(savedCaptor.capture());
        assertEquals(new BigDecimal("4.49"), savedCaptor.getValue().getReceivedEur());
        assertEquals(new BigDecimal("0.615"), savedCaptor.getValue().getAmountPerShare());
    }

    @Test
    void runningTwice_givesTheSameAmount_insteadOfDoublingIt() {
        // The second run finds the row the first one saved: the amount is set again, not added
        DividendEvent row = new DividendEvent("MU_US_EQ", LocalDate.now().minusDays(5));
        onePage(paid("MU_US_EQ", 5, "0.115", "0.84"));
        when(repository.findByTickerAndPaymentDate("MU_US_EQ", LocalDate.now().minusDays(5))).thenReturn(Optional.of(row));

        service.importPayments();
        service.importPayments();

        assertEquals(new BigDecimal("0.84"), row.getReceivedEur());
    }

    @Test
    void aPaymentWithoutAnAmountInEuros_isSkipped() {
        onePage(paid("MU_US_EQ", 5, "0.115", null), paid("ORCL_US_EQ", 6, "0.50", "3.65"));
        nothingSavedYet();

        service.importPayments();

        verify(repository, times(1)).save(savedCaptor.capture());
        assertEquals("ORCL_US_EQ", savedCaptor.getValue().getTicker());
    }

    // ===== Pages =====

    @Test
    void followsTheNextPages_withoutTheApiPrefix() {
        when(t212Client.fetchDividends(FIRST_PAGE)).thenReturn(new T212DividendPageDto(
                List.of(paid("MU_US_EQ", 5, "0.115", "0.84")), "/api/v0/equity/history/dividends?limit=50&cursor=123"));
        when(t212Client.fetchDividends("/equity/history/dividends?limit=50&cursor=123")).thenReturn(new T212DividendPageDto(
                List.of(paid("ORCL_US_EQ", 40, "0.50", "3.65")), null));
        nothingSavedYet();

        service.importPayments();

        verify(repository, times(2)).save(any());
    }

    @Test
    void stopsAtTheFirstPaymentOlderThan13Months_withoutAskingForMorePages() {
        when(t212Client.fetchDividends(FIRST_PAGE)).thenReturn(new T212DividendPageDto(
                List.of(paidMonthsAgo("MU_US_EQ", 3), paidMonthsAgo("MU_US_EQ", 14), paidMonthsAgo("MU_US_EQ", 15)),
                "/api/v0/equity/history/dividends?limit=50&cursor=123"));
        nothingSavedYet();

        service.importPayments();

        verify(t212Client, times(1)).fetchDividends(anyString());
        verify(repository, times(1)).save(any());
    }

    @Test
    void anOldPaymentWithoutAnAmount_stillStopsTheReading() {
        // The date is checked before the amount: otherwise this one would be skipped and the reading would go on
        when(t212Client.fetchDividends(FIRST_PAGE)).thenReturn(new T212DividendPageDto(
                List.of(new T212DividendDto("MU_US_EQ", null, null, LocalDate.now().minusMonths(14) + "T09:30:00.000+01:00")),
                "/api/v0/equity/history/dividends?limit=50&cursor=123"));

        service.importPayments();

        verify(t212Client, times(1)).fetchDividends(anyString());
    }

    @Test
    void ifALaterPageFails_nothingIsSaved_soTheNextRunStartsClean() {
        when(t212Client.fetchDividends(FIRST_PAGE)).thenReturn(new T212DividendPageDto(
                List.of(paid("MU_US_EQ", 5, "0.115", "0.84")), "/api/v0/equity/history/dividends?limit=50&cursor=123"));
        when(t212Client.fetchDividends("/equity/history/dividends?limit=50&cursor=123")).thenThrow(new RuntimeException("429 Too Many Requests"));

        service.importPayments();

        verify(repository, never()).save(any());
    }
}

package com.llijat.t212visualizer.service;

import com.llijat.t212visualizer.dto.AlphaVantageDividendDto;
import com.llijat.t212visualizer.model.DividendCheck;
import com.llijat.t212visualizer.model.DividendEvent;
import com.llijat.t212visualizer.model.Position;
import com.llijat.t212visualizer.repository.DividendCheckRepository;
import com.llijat.t212visualizer.repository.DividendEventRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DividendImportServiceTest {

    @Mock
    private AlphaVantageClient alphaVantageClient;

    @Mock
    private PositionService positionService;

    // Without stubbing, it answers an empty Map: no ticker resolved, so the T212 one is used
    @Mock
    private CompanyProfileService companyProfileService;

    @Mock
    private DividendEventRepository eventRepository;

    // Without stubbing, findAll answers an empty list: no stock has ever been checked
    @Mock
    private DividendCheckRepository checkRepository;

    @Captor
    private ArgumentCaptor<DividendCheck> checkCaptor;

    @Captor
    private ArgumentCaptor<DividendEvent> eventCaptor;

    // No waiting between requests in the tests: 13 seconds each would make them take minutes
    private DividendImportService serviceWith(int stocksPerDay) {
        return new DividendImportService(alphaVantageClient, positionService, companyProfileService,
                eventRepository, checkRepository, stocksPerDay, 0);
    }

    // Only the ticker matters to the service; the figures are invented
    private static Position position(String ticker) {
        BigDecimal one = BigDecimal.ONE;
        return new Position(ticker, ticker + " Inc.", "US0000000001", one, one, one, one, BigDecimal.ZERO, null, one, "USD", "EUR");
    }

    private void thePortfolioIs(String... tickers) {
        when(positionService.getAllPositions()).thenReturn(Arrays.stream(tickers).map(DividendImportServiceTest::position).toList());
    }

    private void lastChecked(DividendCheck... checks) {
        when(checkRepository.findAll()).thenReturn(List.of(checks));
    }

    private static DividendCheck daysAgo(String ticker, int days) {
        return new DividendCheck(ticker, Instant.now().minus(days, ChronoUnit.DAYS));
    }

    private static AlphaVantageDividendDto paidIn(int days, String amount) {
        LocalDate payment = LocalDate.now().plusDays(days);
        return new AlphaVantageDividendDto(payment.minusDays(14).toString(), payment.toString(), amount);
    }

    // ===== Whose turn it is =====

    @Test
    void neverCheckedStocks_goFirst_thenTheOnesCheckedLongestAgo() {
        thePortfolioIs("MU_US_EQ", "ORCL_US_EQ", "NVDA_US_EQ");
        lastChecked(daysAgo("MU_US_EQ", 3), daysAgo("ORCL_US_EQ", 1));

        serviceWith(2).importDividends();

        InOrder order = inOrder(alphaVantageClient);
        order.verify(alphaVantageClient).fetchDividends("NVDA");
        order.verify(alphaVantageClient).fetchDividends("MU");
        verify(alphaVantageClient, never()).fetchDividends("ORCL");
    }

    @Test
    void onlyUsStocks_areAskedFor() {
        thePortfolioIs("MU_US_EQ", "RWEd_EQ");

        serviceWith(20).importDividends();

        verify(alphaVantageClient).fetchDividends("MU");
        verify(alphaVantageClient, times(1)).fetchDividends(anyString());
    }

    @Test
    void theDailyLimit_countsWhatWasAlreadyCheckedToday() {
        // A restart in the afternoon: the morning run already used one of today's two
        thePortfolioIs("MU_US_EQ", "ORCL_US_EQ", "NVDA_US_EQ");
        lastChecked(new DividendCheck("MU_US_EQ", Instant.now()));

        serviceWith(2).importDividends();

        verify(alphaVantageClient, times(1)).fetchDividends(anyString());
    }

    @Test
    void withTodaysTurnsUsedUp_nothingIsAskedFor() {
        thePortfolioIs("MU_US_EQ", "ORCL_US_EQ", "NVDA_US_EQ");
        lastChecked(new DividendCheck("MU_US_EQ", Instant.now()), new DividendCheck("ORCL_US_EQ", Instant.now()));

        serviceWith(2).importDividends();

        verifyNoInteractions(alphaVantageClient);
    }

    @Test
    void checksFromYesterday_doNotCountForToday() {
        thePortfolioIs("MU_US_EQ", "ORCL_US_EQ");
        lastChecked(daysAgo("MU_US_EQ", 1), daysAgo("ORCL_US_EQ", 1));

        serviceWith(2).importDividends();

        verify(alphaVantageClient, times(2)).fetchDividends(anyString());
    }

    // ===== Marking the turn =====

    @Test
    void aStockThatAnswered_hasItsTurnMarked_now() {
        thePortfolioIs("MU_US_EQ");
        when(alphaVantageClient.fetchDividends("MU")).thenReturn(List.of());

        serviceWith(20).importDividends();

        verify(checkRepository).save(checkCaptor.capture());
        assertEquals("MU_US_EQ", checkCaptor.getValue().getTicker());
        assertTrue(Duration.between(checkCaptor.getValue().getCheckedAt(), Instant.now()).toSeconds() < 5);
    }

    @Test
    void aFailure_doesNotMarkTheTurn_soTheStockIsAskedForAgainNextTime_andTheOthersGoOn() {
        thePortfolioIs("MU_US_EQ", "ORCL_US_EQ");
        when(alphaVantageClient.fetchDividends("MU")).thenThrow(new IllegalStateException("daily limit reached?"));
        when(alphaVantageClient.fetchDividends("ORCL")).thenReturn(List.of());

        serviceWith(20).importDividends();

        verify(checkRepository, times(1)).save(checkCaptor.capture());
        assertEquals("ORCL_US_EQ", checkCaptor.getValue().getTicker());
    }

    // ===== Saving the dividends =====

    @Test
    void aNewDividend_isSavedWithTheT212Ticker_andWhatWasAnnounced() {
        thePortfolioIs("MU_US_EQ");
        when(alphaVantageClient.fetchDividends("MU")).thenReturn(List.of(paidIn(10, "0.115")));
        when(eventRepository.findByTickerAndPaymentDate("MU_US_EQ", LocalDate.now().plusDays(10))).thenReturn(Optional.empty());

        serviceWith(20).importDividends();

        verify(eventRepository).save(eventCaptor.capture());
        DividendEvent saved = eventCaptor.getValue();
        assertNull(saved.getId());
        assertEquals("MU_US_EQ", saved.getTicker());
        assertEquals(LocalDate.now().plusDays(10), saved.getPaymentDate());
        assertEquals(LocalDate.now().minusDays(4), saved.getExDividendDate());
        assertEquals(new BigDecimal("0.115"), saved.getAmountPerShare());
        assertNull(saved.getReceivedEur());
    }

    @Test
    void aKnownDividend_updatesItsRow_insteadOfCreatingAnother() {
        DividendEvent existing = new DividendEvent("MU_US_EQ", LocalDate.now().plusDays(10));
        thePortfolioIs("MU_US_EQ");
        when(alphaVantageClient.fetchDividends("MU")).thenReturn(List.of(paidIn(10, "0.12")));
        when(eventRepository.findByTickerAndPaymentDate("MU_US_EQ", LocalDate.now().plusDays(10))).thenReturn(Optional.of(existing));

        serviceWith(20).importDividends();

        verify(eventRepository).save(eventCaptor.capture());
        assertSame(existing, eventCaptor.getValue());
        assertEquals(new BigDecimal("0.12"), existing.getAmountPerShare());
    }

    @Test
    void years_ofHistory_areLeftOut_keepingOnlyTheLast30DaysAndTheFuture() {
        thePortfolioIs("MU_US_EQ");
        when(alphaVantageClient.fetchDividends("MU")).thenReturn(List.of(
                paidIn(10, "0.115"),       // announced
                paidIn(-20, "0.115"),      // paid three weeks ago: still shown in the calendar
                paidIn(-100, "0.115"),     // last year's: not needed
                new AlphaVantageDividendDto("2026-11-10", "None", "0.115")));   // no payment day: cannot go in the calendar
        when(eventRepository.findByTickerAndPaymentDate(any(), any())).thenReturn(Optional.empty());

        serviceWith(20).importDividends();

        verify(eventRepository, times(2)).save(eventCaptor.capture());
        assertEquals(LocalDate.now().plusDays(10), eventCaptor.getAllValues().get(0).getPaymentDate());
        assertEquals(LocalDate.now().minusDays(20), eventCaptor.getAllValues().get(1).getPaymentDate());
    }

    @Test
    void meta_isAskedByItsCurrentTicker_butSavedUnderTheT212One() {
        thePortfolioIs("FB_US_EQ");
        when(companyProfileService.currentSymbols()).thenReturn(Map.of("FB_US_EQ", "META"));
        when(alphaVantageClient.fetchDividends("META")).thenReturn(List.of(paidIn(10, "0.525")));
        when(eventRepository.findByTickerAndPaymentDate(any(), any())).thenReturn(Optional.empty());

        serviceWith(20).importDividends();

        verify(alphaVantageClient, never()).fetchDividends("FB");
        verify(eventRepository).save(eventCaptor.capture());
        assertEquals("FB_US_EQ", eventCaptor.getValue().getTicker());
        verify(checkRepository).save(checkCaptor.capture());
        assertEquals("FB_US_EQ", checkCaptor.getValue().getTicker());
    }
}

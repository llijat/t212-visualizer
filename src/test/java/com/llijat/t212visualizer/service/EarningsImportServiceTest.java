package com.llijat.t212visualizer.service;

import com.llijat.t212visualizer.dto.FinnhubEarningsDto;
import com.llijat.t212visualizer.model.EarningsEvent;
import com.llijat.t212visualizer.model.Position;
import com.llijat.t212visualizer.repository.EarningsEventRepository;
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
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EarningsImportServiceTest {

    @Mock
    private FinnhubClient finnhubClient;

    @Mock
    private PositionService positionService;

    @Mock
    private EarningsEventRepository repository;

    // Without stubbing, it answers an empty Map: no ticker resolved yet, so the T212 one is used
    @Mock
    private CompanyProfileService companyProfileService;

    @Captor
    private ArgumentCaptor<EarningsEvent> savedCaptor;

    private EarningsImportService service;

    @BeforeEach
    void setUp() {
        service = new EarningsImportService(finnhubClient, positionService, repository, companyProfileService);
    }

    // Only the ticker matters to the service; the figures are invented
    private static Position position(String ticker) {
        BigDecimal one = BigDecimal.ONE;
        return new Position(ticker, ticker + " Inc.", "US0000000001", one, one, one, one, BigDecimal.ZERO, null, one, "USD", "EUR");
    }

    private static FinnhubEarningsDto published(String symbol, int year, int quarter) {
        return new FinnhubEarningsDto(symbol, LocalDate.of(2026, 9, 23), "amc", quarter, year,
                new BigDecimal("1.65"), new BigDecimal("1.79"),
                new BigDecimal("8700000000"), new BigDecimal("8900000000"));
    }

    @Test
    void asksFinnhubOnlyForUsStocks_withTheSymbol_from30DaysAgoTo120DaysAhead() {
        when(positionService.getAllPositions()).thenReturn(List.of(position("MU_US_EQ"), position("RWEd_EQ")));
        LocalDate today = LocalDate.now();

        service.importEarnings();

        verify(finnhubClient).fetchEarnings("MU", today.minusDays(30), today.plusDays(120));
        verify(finnhubClient, times(1)).fetchEarnings(anyString(), any(), any());
    }

    @Test
    void aNewQuarter_isCreatedWithTheT212Ticker_andSaved() {
        FinnhubEarningsDto earnings = published("MU", 2026, 4);
        when(positionService.getAllPositions()).thenReturn(List.of(position("MU_US_EQ")));
        when(finnhubClient.fetchEarnings(eq("MU"), any(), any())).thenReturn(List.of(earnings));
        when(repository.findByTickerAndFiscalYearAndFiscalQuarter("MU_US_EQ", 2026, 4)).thenReturn(Optional.empty());

        service.importEarnings();

        verify(repository).save(savedCaptor.capture());
        EarningsEvent saved = savedCaptor.getValue();
        assertNull(saved.getId());
        assertEquals("MU_US_EQ", saved.getTicker());
        assertEquals(2026, saved.getFiscalYear());
        assertEquals(4, saved.getFiscalQuarter());
        assertEquals(LocalDate.of(2026, 9, 23), saved.getReportDate());
        assertEquals("amc", saved.getReportHour());
        assertEquals(new BigDecimal("1.65"), saved.getEpsEstimate());
        assertEquals(new BigDecimal("1.79"), saved.getEpsActual());
        assertEquals(new BigDecimal("8700000000"), saved.getRevenueEstimate());
        assertEquals(new BigDecimal("8900000000"), saved.getRevenueActual());
    }

    @Test
    void aKnownQuarter_updatesTheExistingRow_insteadOfCreatingAnother() {
        // Saved weeks ago, when it was only announced
        EarningsEvent existing = new EarningsEvent("MU_US_EQ", 2026, 4);
        existing.updateReport(LocalDate.of(2026, 9, 22), "bmo", new BigDecimal("1.60"), null, new BigDecimal("8600000000"), null);
        when(positionService.getAllPositions()).thenReturn(List.of(position("MU_US_EQ")));
        when(finnhubClient.fetchEarnings(eq("MU"), any(), any())).thenReturn(List.of(published("MU", 2026, 4)));
        when(repository.findByTickerAndFiscalYearAndFiscalQuarter("MU_US_EQ", 2026, 4)).thenReturn(Optional.of(existing));

        service.importEarnings();

        verify(repository).save(savedCaptor.capture());
        assertSame(existing, savedCaptor.getValue());
        assertEquals(LocalDate.of(2026, 9, 23), existing.getReportDate());
        assertEquals("amc", existing.getReportHour());
        assertEquals(new BigDecimal("1.79"), existing.getEpsActual());
        assertEquals(new BigDecimal("8900000000"), existing.getRevenueActual());
    }

    @Test
    void savesEveryQuarterFinnhubReturns() {
        when(positionService.getAllPositions()).thenReturn(List.of(position("MU_US_EQ")));
        when(finnhubClient.fetchEarnings(eq("MU"), any(), any()))
                .thenReturn(List.of(published("MU", 2026, 4), published("MU", 2027, 1)));
        when(repository.findByTickerAndFiscalYearAndFiscalQuarter(eq("MU_US_EQ"), eq(2026), eq(4))).thenReturn(Optional.empty());
        when(repository.findByTickerAndFiscalYearAndFiscalQuarter(eq("MU_US_EQ"), eq(2027), eq(1))).thenReturn(Optional.empty());

        service.importEarnings();

        verify(repository, times(2)).save(any(EarningsEvent.class));
    }

    @Test
    void aFailingStock_doesNotStopTheOthers() {
        when(positionService.getAllPositions())
                .thenReturn(List.of(position("MU_US_EQ"), position("ORCL_US_EQ"), position("NVDA_US_EQ")));
        when(finnhubClient.fetchEarnings(eq("MU"), any(), any())).thenReturn(List.of(published("MU", 2026, 4)));
        when(finnhubClient.fetchEarnings(eq("ORCL"), any(), any())).thenThrow(new RuntimeException("429 Too Many Requests"));
        when(finnhubClient.fetchEarnings(eq("NVDA"), any(), any())).thenReturn(List.of(published("NVDA", 2027, 3)));
        when(repository.findByTickerAndFiscalYearAndFiscalQuarter(anyString(), anyInt(), anyInt()))
                .thenReturn(Optional.empty());

        service.importEarnings();

        verify(finnhubClient).fetchEarnings(eq("NVDA"), any(), any());
        verify(repository, times(2)).save(savedCaptor.capture());
        assertEquals("MU_US_EQ", savedCaptor.getAllValues().get(0).getTicker());
        assertEquals("NVDA_US_EQ", savedCaptor.getAllValues().get(1).getTicker());
    }

    @Test
    void withNoResults_nothingIsSaved() {
        when(positionService.getAllPositions()).thenReturn(List.of(position("MU_US_EQ")));
        when(finnhubClient.fetchEarnings(eq("MU"), any(), any())).thenReturn(List.of());

        service.importEarnings();

        verify(repository, never()).save(any());
    }

    @Test
    void meta_isAskedByItsCurrentTicker_butSavedUnderTheT212One() {
        // The calendar looks results up by the T212 ticker, so that one must not change
        when(positionService.getAllPositions()).thenReturn(List.of(position("FB_US_EQ")));
        when(companyProfileService.currentSymbols()).thenReturn(Map.of("FB_US_EQ", "META"));
        when(finnhubClient.fetchEarnings(eq("META"), any(), any())).thenReturn(List.of(published("META", 2026, 3)));
        when(repository.findByTickerAndFiscalYearAndFiscalQuarter("FB_US_EQ", 2026, 3)).thenReturn(Optional.empty());

        service.importEarnings();

        verify(finnhubClient, never()).fetchEarnings(eq("FB"), any(), any());
        verify(repository).save(savedCaptor.capture());
        assertEquals("FB_US_EQ", savedCaptor.getValue().getTicker());
    }

    @Test
    void stocksWithoutAResolvedTicker_keepUsingTheT212One() {
        when(positionService.getAllPositions()).thenReturn(List.of(position("FB_US_EQ"), position("MU_US_EQ")));
        when(companyProfileService.currentSymbols()).thenReturn(Map.of("FB_US_EQ", "META"));

        service.importEarnings();

        verify(finnhubClient).fetchEarnings(eq("META"), any(), any());
        verify(finnhubClient).fetchEarnings(eq("MU"), any(), any());
    }

    @Test
    void theResolvedTickers_areReadOnce_notOncePerStock() {
        when(positionService.getAllPositions())
                .thenReturn(List.of(position("MU_US_EQ"), position("ORCL_US_EQ"), position("NVDA_US_EQ")));

        service.importEarnings();

        verify(companyProfileService, times(1)).currentSymbols();
    }
}

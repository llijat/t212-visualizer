package com.llijat.t212visualizer.service;

import com.llijat.t212visualizer.model.Position;
import com.llijat.t212visualizer.model.PriceClose;
import com.llijat.t212visualizer.model.PriceDay;
import com.llijat.t212visualizer.repository.PriceCloseRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PriceBackfillServiceTest {

    private static final ZoneId MADRID = ZoneId.of("Europe/Madrid");
    private static final LocalDate TODAY = LocalDate.of(2026, 11, 15);
    private static final LocalDate A_MONTH_AGO = TODAY.minusDays(31);

    @Mock
    private AlphaVantageClient alphaVantageClient;
    @Mock
    private PositionService positionService;
    @Mock
    private CompanyProfileService companyProfileService;
    @Mock
    private PriceCloseRepository priceCloseRepository;

    // A clock the test can move to the next day, to see the daily budget come back
    private Clock clock = Clock.fixed(Instant.parse("2026-11-15T11:00:00Z"), MADRID);
    private final Clock movableClock = new Clock() {
        @Override public ZoneId getZone() { return MADRID; }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return clock.instant(); }
    };

    // No waiting between requests: the 13 seconds only matter against the real Alpha Vantage
    private PriceBackfillService serviceWithBudget(int perDay) {
        return new PriceBackfillService(alphaVantageClient, positionService, companyProfileService, priceCloseRepository, movableClock, perDay, 0);
    }

    // A real Position (not a mock): only the ticker matters here
    private static Position position(String ticker) {
        return new Position(ticker, ticker, null, BigDecimal.ONE, BigDecimal.ONE, BigDecimal.ONE,
                BigDecimal.TEN, BigDecimal.ZERO, null, BigDecimal.ONE, "USD", "EUR");
    }

    private void thePortfolioIs(Position... positions) {
        when(positionService.getAllPositions()).thenReturn(List.of(positions));
        when(companyProfileService.currentSymbols()).thenReturn(Map.of());
    }

    private void closesSavedInTheLastMonth(String symbol, long count) {
        when(priceCloseRepository.countByIdSymbolAndIdDayGreaterThanEqual(symbol, A_MONTH_AGO)).thenReturn(count);
    }

    @Test
    void aStockWithoutItsMonth_isAskedFor_andEveryCloseIsSaved() {
        thePortfolioIs(position("AAPL_US_EQ"));
        closesSavedInTheLastMonth("AAPL", 3);   // only the days Finnhub saw while the computer was on
        when(alphaVantageClient.fetchDailyCloses("AAPL")).thenReturn(Map.of(
                LocalDate.of(2026, 11, 13), new BigDecimal("230.50"),
                LocalDate.of(2026, 11, 12), new BigDecimal("228.10")));

        serviceWithBudget(15).backfill();

        ArgumentCaptor<PriceClose> saved = ArgumentCaptor.forClass(PriceClose.class);
        verify(priceCloseRepository, times(2)).save(saved.capture());
        Map<PriceDay, BigDecimal> byDay = saved.getAllValues().stream()
                .collect(Collectors.toMap(PriceClose::getId, PriceClose::getClose));
        assertEquals(new BigDecimal("230.50"), byDay.get(new PriceDay("AAPL", LocalDate.of(2026, 11, 13))));
        assertEquals(new BigDecimal("228.10"), byDay.get(new PriceDay("AAPL", LocalDate.of(2026, 11, 12))));
    }

    @Test
    void aStockThatAlreadyHasItsMonth_isNotAskedFor_soItCostsNothing() {
        thePortfolioIs(position("AAPL_US_EQ"));
        closesSavedInTheLastMonth("AAPL", 15);

        serviceWithBudget(15).backfill();

        verify(alphaVantageClient, never()).fetchDailyCloses(anyString());
    }

    @Test
    void onlyUsStocks_areAskedFor() {
        thePortfolioIs(position("VUSAl_EQ"));

        serviceWithBudget(15).backfill();

        verify(priceCloseRepository, never()).countByIdSymbolAndIdDayGreaterThanEqual(anyString(), any());
        verify(alphaVantageClient, never()).fetchDailyCloses(anyString());
    }

    @Test
    void theCurrentSymbol_isAskedFor_andSaved_whenT212StillUsesTheOldOne() {
        when(positionService.getAllPositions()).thenReturn(List.of(position("FB_US_EQ")));
        when(companyProfileService.currentSymbols()).thenReturn(Map.of("FB_US_EQ", "META"));
        closesSavedInTheLastMonth("META", 0);
        when(alphaVantageClient.fetchDailyCloses("META")).thenReturn(Map.of(LocalDate.of(2026, 11, 13), new BigDecimal("612")));

        serviceWithBudget(15).backfill();

        ArgumentCaptor<PriceClose> saved = ArgumentCaptor.forClass(PriceClose.class);
        verify(priceCloseRepository).save(saved.capture());
        assertEquals("META", saved.getValue().getId().symbol());
    }

    @Test
    void noMoreThanTheDailyBudget_isAskedFor_evenIfItRunsAgainThatDay() {
        thePortfolioIs(position("AAPL_US_EQ"), position("MSFT_US_EQ"), position("NVDA_US_EQ"));
        when(priceCloseRepository.countByIdSymbolAndIdDayGreaterThanEqual(anyString(), eq(A_MONTH_AGO))).thenReturn(0L);
        when(alphaVantageClient.fetchDailyCloses(anyString())).thenReturn(Map.of());
        PriceBackfillService service = serviceWithBudget(2);

        service.backfill();
        service.backfill();   // 6 hours later, the same day

        verify(alphaVantageClient, times(2)).fetchDailyCloses(anyString());
        verify(alphaVantageClient, never()).fetchDailyCloses("NVDA");
    }

    @Test
    void theNextDay_theBudgetIsBack() {
        thePortfolioIs(position("AAPL_US_EQ"), position("MSFT_US_EQ"));
        when(priceCloseRepository.countByIdSymbolAndIdDayGreaterThanEqual(anyString(), any())).thenReturn(0L);
        when(alphaVantageClient.fetchDailyCloses(anyString())).thenReturn(Map.of());
        PriceBackfillService service = serviceWithBudget(1);

        service.backfill();
        clock = Clock.fixed(Instant.parse("2026-11-16T11:00:00Z"), MADRID);
        service.backfill();

        verify(alphaVantageClient, times(2)).fetchDailyCloses(anyString());
    }

    @Test
    void aFailedRequest_spendsTheBudget_butTheNextStockIsStillAskedFor() {
        thePortfolioIs(position("AAPL_US_EQ"), position("MSFT_US_EQ"), position("NVDA_US_EQ"));
        when(priceCloseRepository.countByIdSymbolAndIdDayGreaterThanEqual(anyString(), eq(A_MONTH_AGO))).thenReturn(0L);
        when(alphaVantageClient.fetchDailyCloses("AAPL")).thenThrow(new IllegalStateException("daily limit reached?"));
        when(alphaVantageClient.fetchDailyCloses("MSFT")).thenReturn(Map.of());

        serviceWithBudget(2).backfill();

        verify(alphaVantageClient).fetchDailyCloses("MSFT");
        verify(alphaVantageClient, never()).fetchDailyCloses("NVDA");   // AAPL's failure used one of the two
    }
}

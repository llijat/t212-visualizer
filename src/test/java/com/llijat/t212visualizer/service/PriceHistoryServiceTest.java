package com.llijat.t212visualizer.service;

import com.llijat.t212visualizer.dto.PriceSeries;
import com.llijat.t212visualizer.model.Position;
import com.llijat.t212visualizer.model.PriceClose;
import com.llijat.t212visualizer.model.PriceDay;
import com.llijat.t212visualizer.repository.PriceCloseRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PriceHistoryServiceTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-11-15T11:00:00Z"), ZoneId.of("Europe/Madrid"));
    private static final LocalDate FROM = LocalDate.of(2026, 8, 12);   // 95 days back: 3 months of sessions, for the 1W, 1M and 3M mini charts

    @Mock
    private CompanyProfileService companyProfileService;
    @Mock
    private PositionService positionService;
    @Mock
    private PriceCloseRepository priceCloseRepository;

    private PriceHistoryService service;

    @BeforeEach
    void setUp() {
        service = new PriceHistoryService(companyProfileService, positionService, priceCloseRepository, CLOCK);
    }

    // A real Position (not a mock): only the ticker matters here
    private static Position position(String ticker) {
        return new Position(ticker, ticker, null, BigDecimal.ONE, BigDecimal.ONE, BigDecimal.ONE,
                BigDecimal.TEN, BigDecimal.ZERO, null, BigDecimal.ONE, "USD", "EUR");
    }

    // The closes come from the database oldest first, every symbol mixed, as the query orders them
    private void theSavedClosesAre(Object... symbolDayClose) {
        List<PriceClose> closes = new ArrayList<>();
        for (int i = 0; i < symbolDayClose.length; i += 3) {
            closes.add(new PriceClose(new PriceDay((String) symbolDayClose[i], LocalDate.of(2026, 11, (int) symbolDayClose[i + 1])),
                    new BigDecimal((String) symbolDayClose[i + 2])));
        }
        when(priceCloseRepository.findByIdDayGreaterThanEqualOrderByIdDayAsc(FROM)).thenReturn(closes);
    }

    private void thePortfolioIs(Position... positions) {
        when(positionService.getAllPositions()).thenReturn(List.of(positions));
        when(companyProfileService.currentSymbols()).thenReturn(Map.of());
    }

    @Test
    void eachStock_getsItsOwnCloses_inOrder_underItsT212Ticker() {
        thePortfolioIs(position("AAPL_US_EQ"), position("MSFT_US_EQ"));
        theSavedClosesAre(
                "AAPL", 10, "200", "MSFT", 10, "400",
                "AAPL", 11, "210", "MSFT", 11, "380",
                "AAPL", 12, "220");

        Map<String, PriceSeries> series = service.getRecentSeries();

        assertEquals(List.of(new BigDecimal("200"), new BigDecimal("210"), new BigDecimal("220")), series.get("AAPL_US_EQ").closes());
        assertEquals(List.of(new BigDecimal("400"), new BigDecimal("380")), series.get("MSFT_US_EQ").closes());
    }

    @Test
    void theMove_isFromTheFirstCloseToTheLast_withTwoDecimals() {
        thePortfolioIs(position("AAPL_US_EQ"), position("MSFT_US_EQ"));
        theSavedClosesAre(
                "AAPL", 10, "200", "MSFT", 10, "300",
                "AAPL", 11, "250", "MSFT", 11, "290",
                "AAPL", 12, "220", "MSFT", 12, "299");

        Map<String, PriceSeries> series = service.getRecentSeries();

        assertEquals(new BigDecimal("10.00"), series.get("AAPL_US_EQ").changePercent());   // the 250 in the middle does not count
        assertEquals(new BigDecimal("-0.33"), series.get("MSFT_US_EQ").changePercent());   // -1 of 300 = -0.333...
    }

    @Test
    void oneCloseIsNotALine_soItIsLeftOut() {
        thePortfolioIs(position("AAPL_US_EQ"));
        theSavedClosesAre("AAPL", 12, "220");

        assertFalse(service.getRecentSeries().containsKey("AAPL_US_EQ"));
    }

    @Test
    void aStockWithoutCloses_isLeftOut_andKeepsItsWeightBar() {
        thePortfolioIs(position("VUSAl_EQ"));
        theSavedClosesAre("AAPL", 10, "200", "AAPL", 11, "210");   // closes of a stock that was sold

        assertEquals(Map.of(), service.getRecentSeries());
    }

    @Test
    void theCurrentSymbol_findsTheCloses_whenT212StillUsesTheOldOne() {
        when(positionService.getAllPositions()).thenReturn(List.of(position("FB_US_EQ")));
        when(companyProfileService.currentSymbols()).thenReturn(Map.of("FB_US_EQ", "META"));
        theSavedClosesAre("META", 10, "600", "META", 11, "612");

        assertEquals(new BigDecimal("2.00"), service.getRecentSeries().get("FB_US_EQ").changePercent());
    }

    @Test
    void aFirstCloseOfZero_isNoMove_insteadOfDividingByZero() {
        thePortfolioIs(position("AAPL_US_EQ"));
        theSavedClosesAre("AAPL", 10, "0", "AAPL", 11, "5");

        assertEquals(BigDecimal.ZERO, service.getRecentSeries().get("AAPL_US_EQ").changePercent());
    }
}

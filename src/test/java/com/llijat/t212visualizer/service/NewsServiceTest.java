package com.llijat.t212visualizer.service;

import com.llijat.t212visualizer.dto.FinnhubNewsDto;
import com.llijat.t212visualizer.dto.NewsDay;
import com.llijat.t212visualizer.model.Position;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NewsServiceTest {

    @Mock
    private FinnhubClient finnhubClient;

    @Mock
    private PositionService positionService;

    @Mock
    private CompanyProfileService companyProfileService;

    private NewsService newsService;

    @BeforeEach
    void setUp() {
        newsService = new NewsService(finnhubClient, positionService, companyProfileService);
    }

    private static FinnhubNewsDto news(long id, String related, String utc) {
        return new FinnhubNewsDto("company", Instant.parse(utc).getEpochSecond(), "Headline " + id,
                "https://example.com/" + id, id, related, "", "Summary " + id, "Sample Wire");
    }

    private static Position position(String ticker, String symbol) {
        Position position = mock(Position.class);
        when(position.getTicker()).thenReturn(ticker);
        // Only US positions ask for their symbol: the others are skipped before
        if (ticker.contains("_US_")) when(position.getSymbol()).thenReturn(symbol);
        return position;
    }

    // ===== Counting the news of each position =====

    @Test
    void eachSymbol_countsItsNews_inAlphabeticalOrder() {
        // Built before stubbing: Mockito cannot set up one mock while it is setting up another
        List<Position> positions = List.of(position("MSFT_US_EQ", "MSFT"), position("AAPL_US_EQ", "AAPL"));
        when(positionService.getAllPositions()).thenReturn(positions);
        when(companyProfileService.currentSymbols()).thenReturn(Map.of());
        when(finnhubClient.fetchCompanyNews(eq("MSFT"), any(), any())).thenReturn(List.of(
                news(1, "MSFT", "2026-11-16T10:00:00Z"), news(2, "MSFT", "2026-11-15T10:00:00Z"), news(3, "MSFT", "2026-11-14T10:00:00Z")));
        when(finnhubClient.fetchCompanyNews(eq("AAPL"), any(), any())).thenReturn(List.of(
                news(4, "AAPL", "2026-11-16T11:00:00Z")));

        newsService.refreshPortfolioNews();

        assertEquals(List.of("AAPL", "MSFT"), List.copyOf(newsService.getNewsCountBySymbol().keySet()));
        assertEquals(1L, newsService.getNewsCountBySymbol().get("AAPL"));
        assertEquals(3L, newsService.getNewsCountBySymbol().get("MSFT"));
    }

    @Test
    void newsWithoutASymbol_areNotCounted_andDoNotBreakTheCount() {
        // Built before stubbing: Mockito cannot set up one mock while it is setting up another
        List<Position> positions = List.of(position("MSFT_US_EQ", "MSFT"));
        when(positionService.getAllPositions()).thenReturn(positions);
        when(companyProfileService.currentSymbols()).thenReturn(Map.of());
        when(finnhubClient.fetchCompanyNews(eq("MSFT"), any(), any())).thenReturn(List.of(
                news(1, "MSFT", "2026-11-16T10:00:00Z"), news(2, null, "2026-11-16T09:00:00Z"), news(3, " ", "2026-11-16T08:00:00Z")));

        newsService.refreshPortfolioNews();

        assertEquals(Map.of("MSFT", 1L), newsService.getNewsCountBySymbol());
    }

    @Test
    void beforeTheFirstRefresh_thereIsNothingToCount() {
        assertTrue(newsService.getNewsCountBySymbol().isEmpty());
        assertTrue(newsService.getTickerBySymbol().isEmpty());
        assertTrue(newsService.getPortfolioNewsByDay().isEmpty());
    }

    // ===== The T212 ticker of each symbol, for the logos =====

    @Test
    void eachSymbol_remembersItsT212Ticker_evenWhenTheSymbolChanged() {
        // T212 still says FB; OpenFIGI found that it is META now
        // Built before stubbing: Mockito cannot set up one mock while it is setting up another
        List<Position> positions = List.of(position("FB_US_EQ", "FB"), position("AAPL_US_EQ", "AAPL"));
        when(positionService.getAllPositions()).thenReturn(positions);
        when(companyProfileService.currentSymbols()).thenReturn(Map.of("FB_US_EQ", "META"));
        when(finnhubClient.fetchCompanyNews(any(), any(), any())).thenReturn(List.of());

        newsService.refreshPortfolioNews();

        assertEquals(Map.of("META", "FB_US_EQ", "AAPL", "AAPL_US_EQ"), newsService.getTickerBySymbol());
    }

    @Test
    void positionsOutsideTheUs_haveNoTicker_becauseTheyHaveNoNews() {
        // Built before stubbing: Mockito cannot set up one mock while it is setting up another
        List<Position> positions = List.of(position("SGLNl_EQ", "SGLN"), position("AAPL_US_EQ", "AAPL"));
        when(positionService.getAllPositions()).thenReturn(positions);
        when(companyProfileService.currentSymbols()).thenReturn(Map.of());
        when(finnhubClient.fetchCompanyNews(eq("AAPL"), any(), any())).thenReturn(List.of());

        newsService.refreshPortfolioNews();

        assertEquals(Map.of("AAPL", "AAPL_US_EQ"), newsService.getTickerBySymbol());
    }

    // ===== Grouped by day =====

    @Test
    void thePortfolioNews_comeGroupedByDay() {
        // Built before stubbing: Mockito cannot set up one mock while it is setting up another
        List<Position> positions = List.of(position("MSFT_US_EQ", "MSFT"));
        when(positionService.getAllPositions()).thenReturn(positions);
        when(companyProfileService.currentSymbols()).thenReturn(Map.of());
        when(finnhubClient.fetchCompanyNews(eq("MSFT"), any(), any())).thenReturn(List.of(
                news(1, "MSFT", "2026-11-16T10:00:00Z"), news(2, "MSFT", "2026-11-14T10:00:00Z")));

        newsService.refreshPortfolioNews();
        List<NewsDay> days = newsService.getPortfolioNewsByDay();

        assertEquals(List.of(LocalDate.of(2026, 11, 16), LocalDate.of(2026, 11, 14)), days.stream().map(NewsDay::date).toList());
    }

    @Test
    void theMarketNews_comeGroupedByDay() {
        when(finnhubClient.fetchGeneralNews()).thenReturn(List.of(
                news(1, "", "2026-11-15T10:00:00Z"), news(2, "", "2026-11-16T10:00:00Z")));

        newsService.refreshFromFinnhub();
        List<NewsDay> days = newsService.getMarketNewsByDay();

        assertEquals(LocalDate.of(2026, 11, 16), days.get(0).date());
        assertEquals(2, days.size());
    }
}

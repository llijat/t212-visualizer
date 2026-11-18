package com.llijat.t212visualizer.service;

import com.llijat.t212visualizer.dto.FinnhubQuoteDto;
import com.llijat.t212visualizer.dto.HoldingMove;
import com.llijat.t212visualizer.model.Position;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class QuoteServiceTest {

    @Mock
    private FinnhubClient finnhubClient;

    @Mock
    private PositionService positionService;

    @Mock
    private CompanyProfileService companyProfileService;

    private QuoteService quoteService;

    @BeforeEach
    void setUp() {
        quoteService = new QuoteService(finnhubClient, positionService, companyProfileService);
    }

    // A real Position (not a mock): only the ticker and what it is worth today matter here
    private static Position position(String ticker, String value) {
        return new Position(ticker, ticker, null, BigDecimal.ONE, BigDecimal.ONE, BigDecimal.ONE,
                new BigDecimal(value), BigDecimal.ZERO, null, BigDecimal.ONE, "USD", "EUR");
    }

    private static FinnhubQuoteDto moved(String percent) {
        return new FinnhubQuoteDto(new BigDecimal("100"), new BigDecimal(percent), 1794862800L);
    }

    private void thePortfolioIs(Position... positions) {
        when(positionService.getAllPositions()).thenReturn(List.of(positions));
        when(companyProfileService.currentSymbols()).thenReturn(Map.of());
    }

    // ===== Refreshing =====

    @Test
    void onlyUsPositions_areAskedFor_becauseFinnhubHasNoOthers() {
        thePortfolioIs(position("AAPL_US_EQ", "1500"), position("VUSAl_EQ", "8300"));
        when(finnhubClient.fetchQuote("AAPL")).thenReturn(moved("-4"));

        quoteService.refreshQuotes();

        verify(finnhubClient).fetchQuote("AAPL");
        verify(finnhubClient, never()).fetchQuote("VUSAl");
    }

    @Test
    void theCurrentSymbol_isAskedFor_whenT212StillUsesTheOldOne() {
        when(positionService.getAllPositions()).thenReturn(List.of(position("FB_US_EQ", "1000")));
        when(companyProfileService.currentSymbols()).thenReturn(Map.of("FB_US_EQ", "META"));
        when(finnhubClient.fetchQuote("META")).thenReturn(moved("2"));

        quoteService.refreshQuotes();

        assertEquals("META", quoteService.getMoves().get(0).symbol());
        assertEquals("FB_US_EQ", quoteService.getMoves().get(0).ticker());
    }

    @Test
    void aSymbolFinnhubDoesNotKnow_isLeftOut() {
        thePortfolioIs(position("AAPL_US_EQ", "1500"), position("DSVSF_US_EQ", "300"));
        when(finnhubClient.fetchQuote("AAPL")).thenReturn(moved("-4"));
        when(finnhubClient.fetchQuote("DSVSF")).thenReturn(new FinnhubQuoteDto(BigDecimal.ZERO, null, 0L));

        quoteService.refreshQuotes();

        assertEquals(List.of("AAPL"), quoteService.getMoves().stream().map(HoldingMove::symbol).toList());
    }

    @Test
    void ifOneQuoteFails_theOthersStillArrive() {
        thePortfolioIs(position("AAPL_US_EQ", "1500"), position("MSFT_US_EQ", "200"));
        when(finnhubClient.fetchQuote("AAPL")).thenThrow(new RuntimeException("Too many requests"));
        when(finnhubClient.fetchQuote("MSFT")).thenReturn(moved("6"));

        quoteService.refreshQuotes();

        assertEquals(List.of("MSFT"), quoteService.getMoves().stream().map(HoldingMove::symbol).toList());
    }

    @Test
    void aSoldPosition_disappearsAtTheNextRefresh() {
        thePortfolioIs(position("AAPL_US_EQ", "1500"), position("MSFT_US_EQ", "200"));
        when(finnhubClient.fetchQuote(anyString())).thenReturn(moved("1"));
        quoteService.refreshQuotes();

        // MSFT is sold: the next refresh starts from an empty map, so its old quote does not stay
        when(positionService.getAllPositions()).thenReturn(List.of(position("AAPL_US_EQ", "1500")));
        quoteService.refreshQuotes();

        assertEquals(List.of("AAPL"), quoteService.getMoves().stream().map(HoldingMove::symbol).toList());
    }

    // ===== The moves, with their weight =====

    @Test
    void theWeight_isTheShareOfTheWholePortfolio_includingWhatIsNotFromTheUs() {
        // The example from the lesson: a portfolio of 10,000 €
        thePortfolioIs(position("AAPL_US_EQ", "1500"), position("MSFT_US_EQ", "200"), position("VUSAl_EQ", "8300"));
        when(finnhubClient.fetchQuote("AAPL")).thenReturn(moved("-4"));
        when(finnhubClient.fetchQuote("MSFT")).thenReturn(moved("6"));

        quoteService.refreshQuotes();
        List<HoldingMove> moves = quoteService.getMoves();

        assertEquals(new BigDecimal("2.0"), moves.get(0).weightPercent());
        assertEquals(new BigDecimal("15.0"), moves.get(1).weightPercent());
    }

    @Test
    void whatMovesMost_upOrDown_comesFirst() {
        thePortfolioIs(position("AAPL_US_EQ", "1500"), position("MSFT_US_EQ", "200"), position("NVDA_US_EQ", "900"));
        when(finnhubClient.fetchQuote("AAPL")).thenReturn(moved("-4"));
        when(finnhubClient.fetchQuote("MSFT")).thenReturn(moved("6"));
        when(finnhubClient.fetchQuote("NVDA")).thenReturn(moved("0.5"));

        quoteService.refreshQuotes();

        assertEquals(List.of("MSFT", "AAPL", "NVDA"), quoteService.getMoves().stream().map(HoldingMove::symbol).toList());
    }

    @Test
    void theWeight_isRoundedToOneDecimal() {
        // 1 of 3 € is 33.333... %: dividing a BigDecimal needs to be told how to round
        thePortfolioIs(position("AAPL_US_EQ", "1"), position("VUSAl_EQ", "2"));
        when(finnhubClient.fetchQuote("AAPL")).thenReturn(moved("1"));

        quoteService.refreshQuotes();

        assertEquals(new BigDecimal("33.3"), quoteService.getMoves().get(0).weightPercent());
    }

    @Test
    void beforeTheFirstRefresh_thereAreNoMoves() {
        when(positionService.getAllPositions()).thenReturn(List.of(position("AAPL_US_EQ", "1500")));

        assertTrue(quoteService.getMoves().isEmpty());
    }

    @Test
    void withAnEmptyPortfolio_nothingIsDividedByZero() {
        when(positionService.getAllPositions()).thenReturn(List.of());

        assertTrue(quoteService.getMoves().isEmpty());
    }

    // ===== Which day the moves are from =====

    private static FinnhubQuoteDto movedAt(String utc) {
        return new FinnhubQuoteDto(new BigDecimal("100"), BigDecimal.ONE, Instant.parse(utc).getEpochSecond());
    }

    @Test
    void theSession_isTheDayOfTheNewestQuote() {
        thePortfolioIs(position("AAPL_US_EQ", "1500"), position("MSFT_US_EQ", "200"));
        when(finnhubClient.fetchQuote("AAPL")).thenReturn(movedAt("2026-11-13T20:00:00Z"));
        when(finnhubClient.fetchQuote("MSFT")).thenReturn(movedAt("2026-11-16T20:00:00Z"));

        quoteService.refreshQuotes();

        assertEquals(Optional.of(LocalDate.of(2026, 11, 16)), quoteService.getLastSession());
    }

    @Test
    void theSession_isTheDayInSpain() {
        // Monday 16 at 23:30 UTC is already Tuesday 17 at 00:30 in Spain
        thePortfolioIs(position("AAPL_US_EQ", "1500"));
        when(finnhubClient.fetchQuote("AAPL")).thenReturn(movedAt("2026-11-16T23:30:00Z"));

        quoteService.refreshQuotes();

        assertEquals(Optional.of(LocalDate.of(2026, 11, 17)), quoteService.getLastSession());
    }

    @Test
    void withoutQuotes_thereIsNoSession() {
        assertEquals(Optional.empty(), quoteService.getLastSession());
    }
}

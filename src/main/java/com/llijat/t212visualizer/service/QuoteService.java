package com.llijat.t212visualizer.service;

import com.llijat.t212visualizer.dto.FinnhubQuoteDto;
import com.llijat.t212visualizer.dto.HoldingMove;
import com.llijat.t212visualizer.model.Position;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.*;

@Service
public class QuoteService {
    private static final Logger log = LoggerFactory.getLogger(QuoteService.class);
    private static final BigDecimal HUNDRED = new BigDecimal("100");
    private static final ZoneId SPAIN = ZoneId.of("Europe/Madrid");

    private final FinnhubClient finnhubClient;
    private final PositionService positionService;
    private final CompanyProfileService companyProfileService;
    private volatile Map<String, FinnhubQuoteDto> quoteByTicker = Map.of();


    public QuoteService(FinnhubClient finnhubClient, PositionService positionService, CompanyProfileService companyProfileService) {
        this.finnhubClient = finnhubClient;
        this.positionService = positionService;
        this.companyProfileService = companyProfileService;
    }

    public List<HoldingMove> getMoves() {
        List<Position> positions = positionService.getAllPositions();
        BigDecimal total = positions.stream()
                .map(Position::getCurrentValue)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        if (total.signum() == 0) return List.of();

        Map<String, String> symbols = companyProfileService.currentSymbols();
        List<HoldingMove> moves = new ArrayList<>();
        for (Position position : positions) {
            FinnhubQuoteDto quote = quoteByTicker.get(position.getTicker());
            if (quote == null || position.getCurrentValue() == null) continue;
            String symbol = symbols.getOrDefault(position.getTicker(), position.getSymbol());
            BigDecimal weight = position.getCurrentValue().multiply(HUNDRED).divide(total, 1, RoundingMode.HALF_UP);
            moves.add(new HoldingMove(symbol, position.getTicker(), quote.changePercent(), weight));
        }

        moves.sort(Comparator.comparing((HoldingMove move) -> move.changePercent().abs()).reversed());
        return moves;
    }

    public Optional<LocalDate> getLastSession() {
        return quoteByTicker.values().stream()
                .map(FinnhubQuoteDto::time)
                .max(Comparator.naturalOrder())
                .map(time -> LocalDate.ofInstant(Instant.ofEpochSecond(time), SPAIN));
    }

    @Scheduled(fixedRate = 15*60*1000, initialDelay = 3*60*1000)
    public void refreshQuotes() {
        Map<String, FinnhubQuoteDto> quotes = new HashMap<>();
        Map<String, String> symbols = companyProfileService.currentSymbols();

        for (Position position : positionService.getAllPositions()) {
            if (!position.getTicker().contains("_US_")) continue;
            try {
                String symbol = symbols.getOrDefault(position.getTicker(), position.getSymbol());
                FinnhubQuoteDto quote = finnhubClient.fetchQuote(symbol);
                if (quote != null && quote.isKnown()) quotes.put(position.getTicker(), quote);
            } catch (Exception e) {
                log.warn("Could not load the quote of {}: {}", position.getTicker(), e.getMessage());
            }
        }

        quoteByTicker = quotes;
        log.info("Refreshed {} quotes.", quotes.size());
    }
}

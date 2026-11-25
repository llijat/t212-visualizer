package com.llijat.t212visualizer.service;

import com.llijat.t212visualizer.dto.FinnhubQuoteDto;
import com.llijat.t212visualizer.dto.HoldingMove;
import com.llijat.t212visualizer.dto.MetalMove;
import com.llijat.t212visualizer.model.Position;
import com.llijat.t212visualizer.model.PriceClose;
import com.llijat.t212visualizer.model.PriceDay;
import com.llijat.t212visualizer.repository.PriceCloseRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.*;

@Service
public class QuoteService {
    private static final Logger log = LoggerFactory.getLogger(QuoteService.class);
    private static final BigDecimal HUNDRED = new BigDecimal("100");
    private final Clock clock;
    private static final List<String> METAL_ETFS = List.of("GLD","SLV");
    private static final ZoneId NEW_YORK = ZoneId.of("America/New_York");

    private final FinnhubClient finnhubClient;
    private final PositionService positionService;
    private final PriceCloseRepository priceCloseRepository;
    private final CompanyProfileService companyProfileService;
    private volatile Map<String, FinnhubQuoteDto> quoteByTicker = Map.of();
    private volatile Map<String, FinnhubQuoteDto> quoteByMetalEtf = Map.of();


    public QuoteService(FinnhubClient finnhubClient, PositionService positionService, CompanyProfileService companyProfileService, Clock clock, PriceCloseRepository priceCloseRepository) {
        this.finnhubClient = finnhubClient;
        this.positionService = positionService;
        this.companyProfileService = companyProfileService;
        this.clock = clock;
        this.priceCloseRepository = priceCloseRepository;
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
                .map(time -> LocalDate.ofInstant(Instant.ofEpochSecond(time), clock.getZone()));
    }

    public List<MetalMove> getMetals() {
        List<MetalMove> metals = new ArrayList<>();
        for (String etf : METAL_ETFS) {
            FinnhubQuoteDto quote = quoteByMetalEtf.get(etf);
            if (quote == null) continue;
            metals.add(new MetalMove(etf, quote.changePercent()));
        }
        return metals;
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
                if (quote != null && quote.isKnown() && quote.price() != null) {
                    quotes.put(position.getTicker(), quote);
                    LocalDate day = LocalDate.ofInstant(Instant.ofEpochSecond(quote.time()), NEW_YORK);
                    priceCloseRepository.save(new PriceClose(new PriceDay(symbol, day), quote.price()));
                }
            } catch (Exception e) {
                log.warn("Could not load the quote of {}: {}", position.getTicker(), e.getMessage());
            }
        }

        quoteByTicker = quotes;
        quoteByMetalEtf = fetchMetals();
        log.info("Refreshed {} quotes.", quotes.size());
    }

    private Map<String, FinnhubQuoteDto> fetchMetals() {
        Map<String, FinnhubQuoteDto> quotes = new HashMap<>();
        for (String etf: METAL_ETFS) {
            try {
                FinnhubQuoteDto quote = finnhubClient.fetchQuote(etf);
                if (quote != null && quote.isKnown()) quotes.put(etf, quote);
            }
            catch (Exception e) {
                log.warn("Could not load the quote of {}: {}", etf, e.getMessage());
            }
        }
        return quotes;
    }
}

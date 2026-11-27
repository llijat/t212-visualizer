package com.llijat.t212visualizer.service;

import com.llijat.t212visualizer.dto.FinnhubQuoteDto;
import com.llijat.t212visualizer.dto.HoldingMove;
import com.llijat.t212visualizer.dto.CommodityMove;
import com.llijat.t212visualizer.dto.Subsector;
import com.llijat.t212visualizer.model.Position;
import com.llijat.t212visualizer.model.PriceClose;
import com.llijat.t212visualizer.model.PriceDay;
import com.llijat.t212visualizer.repository.PriceCloseRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
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
    private static final List<String> COMMODITIES = List.of("GLD", "SLV", "USO", "BINANCE:BTCUSDT");
    private static final ZoneId NEW_YORK = ZoneId.of("America/New_York");

    private final FinnhubClient finnhubClient;
    private final PositionService positionService;
    private final PriceCloseRepository priceCloseRepository;
    private final CompanyProfileService companyProfileService;
    private volatile Map<String, FinnhubQuoteDto> quoteByTicker = Map.of();
    private volatile Map<String, FinnhubQuoteDto> quoteByCommodity = Map.of();
    private volatile Map<String, FinnhubQuoteDto> quoteBySectorEtf = Map.of();
    private final long requestDelayMs;


    public QuoteService(FinnhubClient finnhubClient, PositionService positionService, CompanyProfileService companyProfileService, Clock clock, PriceCloseRepository priceCloseRepository, @Value("${finnhub.request-delay-ms}") long requestDelayMs) {
        this.finnhubClient = finnhubClient;
        this.positionService = positionService;
        this.companyProfileService = companyProfileService;
        this.clock = clock;
        this.priceCloseRepository = priceCloseRepository;
        this.requestDelayMs = requestDelayMs;
    }

    public Map<String, BigDecimal> getSectorEtfMoves() {
        Map<String, BigDecimal> moves = new HashMap<>();
        quoteBySectorEtf.forEach((etf, quote) -> moves.put(etf, quote.changePercent()));
        return moves;
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

    public List<CommodityMove> getCommodities() {
        List<CommodityMove> metals = new ArrayList<>();
        for (String symbol : COMMODITIES) {
            FinnhubQuoteDto quote = quoteByCommodity.get(symbol);
            if (quote == null) continue;
            metals.add(new CommodityMove(symbol, quote.changePercent()));
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
                FinnhubQuoteDto quote = fetchQuote(symbol);
                if (quote != null && quote.isKnown() && quote.price() != null) {
                    quotes.put(position.getTicker(), quote);
                    saveClose(symbol, quote);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            } catch (Exception e) {
                log.warn("Could not load the quote of {}: {}", position.getTicker(), e.getMessage());
            }
        }

        quoteByTicker = quotes;
        quoteByCommodity = fetchCommodities();
        quoteBySectorEtf = fetchSectorEtfs();
        log.info("Refreshed {} quotes and {} sector ETFs.", quotes.size(), quoteBySectorEtf.size());
    }

    private Map<String, FinnhubQuoteDto> fetchCommodities() {
        Map<String, FinnhubQuoteDto> quotes = new HashMap<>();
        for (String symbol : COMMODITIES) {
            try {
                FinnhubQuoteDto quote = fetchQuote(symbol);
                if (quote != null && quote.isKnown()) quotes.put(symbol, quote);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                log.warn("Could not load the quote of {}: {}", symbol, e.getMessage());
            }
        }
        return quotes;
    }

    private Map<String, FinnhubQuoteDto> fetchSectorEtfs() {
        Map<String, FinnhubQuoteDto> quotes = new HashMap<>();
        for (Subsector subsector : SectorCatalog.all()) {
            String etf = subsector.etf();
            try {
                FinnhubQuoteDto quote = fetchQuote(etf);
                if (quote != null && quote.isKnown() && quote.price() != null) {
                    quotes.put(etf, quote);
                    saveClose(etf, quote);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                log.warn("Could not load the quote of {}: {}", etf, e.getMessage());
            }
        }
        return quotes;
    }

    private FinnhubQuoteDto fetchQuote(String symbol) throws InterruptedException {
        Thread.sleep(requestDelayMs);   // waits before each request: at most 60 a minute, even if one fails
        return finnhubClient.fetchQuote(symbol);
    }

    private void saveClose(String symbol, FinnhubQuoteDto quote) {
        LocalDate day = LocalDate.ofInstant(Instant.ofEpochSecond(quote.time()), NEW_YORK);
        priceCloseRepository.save(new PriceClose(new PriceDay(symbol, day), quote.price()));
    }

}

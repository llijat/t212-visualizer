package com.llijat.t212visualizer.service;

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
import java.time.Clock;
import java.time.LocalDate;
import java.util.Map;


@Service
public class PriceBackfillService {
    private static final Logger log = LoggerFactory.getLogger(PriceBackfillService.class);
    private static final int MIN_CLOSES = 15;
    private static final int DAYS_BACK = 31;

    private final AlphaVantageClient alphaVantageClient;
    private final PositionService positionService;
    private final CompanyProfileService companyProfileService;
    private final PriceCloseRepository priceCloseRepository;
    private final Clock clock;
    private final int backfillPerDay;
    private final long requestDelayMs;

    private LocalDate countedDay;
    private int usedToday;

    public PriceBackfillService(AlphaVantageClient alphaVantageClient, PositionService positionService, CompanyProfileService companyProfileService, PriceCloseRepository priceCloseRepository, Clock clock, @Value("${alpha-vantage.backfill-per-day}") int backfillPerDay, @Value("${alpha-vantage.request-delay-ms}") long requestDelayMs) {
        this.alphaVantageClient = alphaVantageClient;
        this.positionService = positionService;
        this.companyProfileService = companyProfileService;
        this.priceCloseRepository = priceCloseRepository;
        this.clock = clock;
        this.backfillPerDay = backfillPerDay;
        this.requestDelayMs = requestDelayMs;
    }

    @Scheduled(fixedRate = 6*60*60*1000, initialDelay = 60*1000)
    public void backfill() {
        LocalDate today = LocalDate.now(clock);
        if (!today.equals(countedDay)) {
            countedDay = today;
            usedToday = 0;
        }

        LocalDate from = today.minusDays(DAYS_BACK);
        Map<String, String> symbols = companyProfileService.currentSymbols();
        try {
            for (Position position : positionService.getAllPositions()) {
                if (!position.getTicker().contains("_US_")) continue;
                String symbol = symbols.getOrDefault(position.getTicker(), position.getSymbol());
                if (priceCloseRepository.countByIdSymbolAndIdDayGreaterThanEqual(symbol, from) >= MIN_CLOSES) continue;   // already has its month
                if (usedToday >= backfillPerDay) break;   // today's budget is spent

                usedToday++;   // before asking: a failed request also counts
                backfillStock(symbol);
                Thread.sleep(requestDelayMs);   // at most 5 requests a minute
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private void backfillStock(String symbol) {
        try {
            for (Map.Entry<LocalDate, BigDecimal> close : alphaVantageClient.fetchDailyCloses(symbol).entrySet()) {
                priceCloseRepository.save(new PriceClose(new PriceDay(symbol, close.getKey()), close.getValue()));
            }
            log.info("Backfilled the closes of {}.", symbol);
        } catch (Exception e) {
            log.warn("Could not backfill the closes of {}: {}", symbol, e.getMessage());
        }
    }

}

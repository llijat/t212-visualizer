package com.llijat.t212visualizer.service;

import com.llijat.t212visualizer.dto.AlphaVantageDividendDto;
import com.llijat.t212visualizer.model.DividendCheck;
import com.llijat.t212visualizer.model.DividendEvent;
import com.llijat.t212visualizer.model.Position;
import com.llijat.t212visualizer.repository.DividendCheckRepository;
import com.llijat.t212visualizer.repository.DividendEventRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.*;

@Service
public class DividendImportService {
    private static final Logger log = LoggerFactory.getLogger(DividendImportService.class);
    private static final int DAYS_BACK = 30;

    private final AlphaVantageClient alphaVantageClient;
    private final PositionService positionService;
    private final CompanyProfileService companyProfileService;
    private final DividendEventRepository dividendEventRepository;
    private final DividendCheckRepository dividendCheckRepository;
    private final int stocksPerDay;
    private final long requestDelayMs;

    public DividendImportService(AlphaVantageClient alphaVantageClient, PositionService positionService, CompanyProfileService companyProfileService,
                                 DividendEventRepository dividendEventRepository, DividendCheckRepository dividendCheckRepository,
                                 @Value("${alpha-vantage.stocks-per-day}") int stocksPerDay,
                                 @Value("${alpha-vantage.request-delay-ms}") long requestDelayMs) {
        this.alphaVantageClient = alphaVantageClient;
        this.positionService = positionService;
        this.companyProfileService = companyProfileService;
        this.dividendEventRepository = dividendEventRepository;
        this.dividendCheckRepository = dividendCheckRepository;
        this.stocksPerDay = stocksPerDay;
        this.requestDelayMs = requestDelayMs;
    }

    @Scheduled(fixedRate = 6*60*60*1000, initialDelay = 5*60*1000)
    public void importDividends() {
        List<Position> turn = todaysTurn();
        if (turn.isEmpty()) return;

        LocalDate from = LocalDate.now().minusDays(DAYS_BACK);
        Map<String, String> symbols = companyProfileService.currentSymbols();
        int checked = 0;
        try {
            for (int i = 0; i < turn.size(); i++) {
                if (i > 0) Thread.sleep(requestDelayMs);
                if (importPosition(turn.get(i), symbols, from)) ++checked;
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        log.info("Checked the dividends of {} stocks.", checked);
    }

    private List<Position> todaysTurn() {
        Map<String, Instant> checkedAt = new HashMap<>();
        for (DividendCheck check : dividendCheckRepository.findAll()) {
            checkedAt.put(check.getTicker(), check.getCheckedAt());
        }

        Instant startOfToday = LocalDate.now().atStartOfDay(ZoneId.systemDefault()).toInstant();
        long checkedToday = checkedAt.values().stream().filter(time -> time.isAfter(startOfToday)).count();
        int left = (int) Math.max(0, stocksPerDay - checkedToday);

        List<Position> usPositions = new ArrayList<>();
        for (Position position : positionService.getAllPositions()) {
            if (position.getTicker().contains("_US_")) usPositions.add(position);
        }
        usPositions.sort(Comparator.comparing(position -> checkedAt.getOrDefault(position.getTicker(), Instant.EPOCH)));
        return usPositions.subList(0, Math.min(left, usPositions.size()));
    }

    private boolean importPosition(Position position, Map<String, String> symbols, LocalDate from) {
        try {
            String symbol = symbols.getOrDefault(position.getTicker(), position.getSymbol());
            for (AlphaVantageDividendDto dividend : alphaVantageClient.fetchDividends(symbol)) {
                LocalDate payment = dividend.paymentDay();
                if (payment == null || payment.isBefore(from)) continue;
                saveDividend(position.getTicker(), dividend);
            }
            dividendCheckRepository.save(new DividendCheck(position.getTicker(), Instant.now()));
            return true;
        } catch (Exception e) {
            log.warn("Could not import the dividends of {}: {}", position.getTicker(), e.getMessage());
            return false;
        }
    }

    private void saveDividend(String ticker, AlphaVantageDividendDto dividend) {
        DividendEvent event = dividendEventRepository.findByTickerAndPaymentDate(ticker, dividend.paymentDay())
                .orElseGet(() -> new DividendEvent(ticker, dividend.paymentDay()));
        event.updateAnnouncement(dividend.exDividendDay(), dividend.amountPerShare());
        dividendEventRepository.save(event);
    }
}
package com.llijat.t212visualizer.service;

import com.llijat.t212visualizer.dto.FinnhubEarningsDto;
import com.llijat.t212visualizer.model.EarningsEvent;
import com.llijat.t212visualizer.model.Position;
import com.llijat.t212visualizer.repository.EarningsEventRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class EarningsImportService {
    private static final Logger log = LoggerFactory.getLogger(EarningsImportService.class);
    private static final int DAYS_BACK = 30;
    private static final int DAYS_AHEAD = 120;

    private final FinnhubClient finnhubClient;
    private final PositionService positionService;
    private final EarningsEventRepository earningsEventRepository;
    private final CompanyProfileService companyProfileService;

    public EarningsImportService(FinnhubClient finnhubClient, PositionService positionService, EarningsEventRepository earningsEventRepository, CompanyProfileService companyProfileService) {
        this.finnhubClient = finnhubClient;
        this.positionService = positionService;
        this.earningsEventRepository = earningsEventRepository;
        this.companyProfileService = companyProfileService;
    }

    @Scheduled(fixedRate = 12*60*60*1000, initialDelay = 60*1000)
    public void importEarnings() {
        LocalDate today = LocalDate.now();
        LocalDate from = today.minusDays(DAYS_BACK);
        LocalDate to = today.plusDays(DAYS_AHEAD);

        int imported = 0;
        Map<String, String> symbols = companyProfileService.currentSymbols();
        for (Position position : positionService.getAllPositions()) {
            if (position.getTicker().contains("_US_")) {
                try {
                    String symbol = symbols.getOrDefault(position.getTicker(), position.getSymbol());
                    List<FinnhubEarningsDto> earningsList = finnhubClient.fetchEarnings(symbol, from, to);
                    for (FinnhubEarningsDto earningsDto : earningsList) {
                        saveEarnings(position.getTicker(), earningsDto);
                    }
                    ++imported;
                }
                catch (Exception e) {
                    log.warn("Could not import the earnings of {}: {}",position.getTicker(),e.getMessage());
                }
            }
        }
        log.info("Imported the earnings of {} stocks.", imported);
    }

    @Scheduled(fixedRate = 60*60*1000, initialDelay = 60*60*1000)
    public void refreshRecentReports() {
        LocalDate today = LocalDate.now();
        LocalDate yesterday = today.minusDays(1);

        List<EarningsEvent> pending = earningsEventRepository.findByReportDateBetweenAndEpsActualIsNull(yesterday, today);
        if (pending.isEmpty()) return;

        Set<String> pendingTickers = new HashSet<>();
        for (EarningsEvent event : pending) {
            pendingTickers.add(event.getTicker());
        }

        int refreshed = 0;
        Map<String, String> symbols = companyProfileService.currentSymbols();
        for (Position position : positionService.getAllPositions()) {
            if (!pendingTickers.contains(position.getTicker())) continue;
            if (importPosition(position, symbols, yesterday, today)) ++refreshed;
        }
        log.info("Refreshed the recent reports of {} stocks.", refreshed);
    }

    private boolean importPosition(Position position, Map<String, String> symbols, LocalDate from, LocalDate to) {
        try {
            String symbol = symbols.getOrDefault(position.getTicker(), position.getSymbol());
            for (FinnhubEarningsDto earningsDto : finnhubClient.fetchEarnings(symbol, from, to)) {
                saveEarnings(position.getTicker(), earningsDto);
            }
            return true;
        } catch (Exception e) {
            log.warn("Could not import the earnings of {}: {}", position.getTicker(), e.getMessage());
            return false;
        }
    }

    private void saveEarnings(String ticker, FinnhubEarningsDto earningsDto) {
        EarningsEvent event = earningsEventRepository.findByTickerAndFiscalYearAndFiscalQuarter(ticker, earningsDto.year(), earningsDto.quarter())
                .orElseGet(() -> new EarningsEvent(ticker, earningsDto.year(), earningsDto.quarter()));

        event.updateReport(earningsDto.date(), earningsDto.hour(), earningsDto.epsEstimate(), earningsDto.epsActual(), earningsDto.revenueEstimate(), earningsDto.revenueActual());

        earningsEventRepository.save(event);

    }
}

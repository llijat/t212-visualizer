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
import java.util.List;

@Service
public class EarningsImportService {
    private static final Logger log = LoggerFactory.getLogger(EarningsImportService.class);
    private static final int DAYS_BACK = 30;
    private static final int DAYS_AHEAD = 120;

    private final FinnhubClient finnhubClient;
    private final PositionService positionService;
    private final EarningsEventRepository earningsEventRepository;

    public EarningsImportService(FinnhubClient finnhubClient, PositionService positionService, EarningsEventRepository earningsEventRepository) {
        this.finnhubClient = finnhubClient;
        this.positionService = positionService;
        this.earningsEventRepository = earningsEventRepository;
    }

    @Scheduled(fixedRate = 12*60*60*1000, initialDelay = 60*1000)
    public void importEarnings() {
        LocalDate today = LocalDate.now();
        LocalDate from = today.minusDays(DAYS_BACK);
        LocalDate to = today.plusDays(DAYS_AHEAD);

        int imported = 0;
        for (Position position : positionService.getAllPositions()) {
            if (position.getTicker().contains("_US_")) {
                try {
                    List<FinnhubEarningsDto> earningsList = finnhubClient.fetchEarnings(position.getSymbol(), from, to);
                    for (FinnhubEarningsDto earningsDto : earningsList) {
                        saveEarnings(position.getTicker(), earningsDto);
                    }
                    ++imported;
                }
                catch (Exception e) {
                    log.warn("Could not import the earnings of {}: {}",position.getSymbol(),e.getMessage());
                }
            }
        }
        log.info("Imported the earnings of {} stocks.", imported);
    }

    private void saveEarnings(String ticker, FinnhubEarningsDto earningsDto) {
        EarningsEvent event = earningsEventRepository.findByTickerAndFiscalYearAndFiscalQuarter(ticker, earningsDto.year(), earningsDto.quarter())
                .orElseGet(() -> new EarningsEvent(ticker, earningsDto.year(), earningsDto.quarter()));

        event.updateReport(earningsDto.date(), earningsDto.hour(), earningsDto.epsEstimate(), earningsDto.epsActual(), earningsDto.revenueEstimate(), earningsDto.revenueActual());

        earningsEventRepository.save(event);

    }
}

package com.llijat.t212visualizer.service;

import com.llijat.t212visualizer.dto.PriceSeries;
import com.llijat.t212visualizer.model.Position;
import com.llijat.t212visualizer.model.PriceClose;
import com.llijat.t212visualizer.repository.PriceCloseRepository;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class PriceHistoryService {
    private static final int DAYS_BACK = 95;

    private final CompanyProfileService companyProfileService;
    private final PositionService positionService;
    private final PriceCloseRepository priceCloseRepository;
    private final Clock clock;

    public PriceHistoryService(CompanyProfileService companyProfileService, PositionService positionService, PriceCloseRepository priceCloseRepository, Clock clock) {
        this.companyProfileService = companyProfileService;
        this.positionService = positionService;
        this.priceCloseRepository = priceCloseRepository;
        this.clock = clock;
    }

    public Map<String, PriceSeries> getRecentSeries() {
        LocalDate from = LocalDate.now(clock).minusDays(DAYS_BACK);
        Map<String, List<BigDecimal>> closesBySymbol = new HashMap<>();
        for (PriceClose close : priceCloseRepository.findByIdDayGreaterThanEqualOrderByIdDayAsc(from)) {
            closesBySymbol.computeIfAbsent(close.getId().symbol(), symbol -> new ArrayList<>()).add(close.getClose());
        }

        Map<String, String> symbols = companyProfileService.currentSymbols();
        Map<String, PriceSeries> series = new HashMap<>();
        for (Position position : positionService.getAllPositions()) {
            String symbol = symbols.getOrDefault(position.getTicker(), position.getSymbol());
            List<BigDecimal> closes = closesBySymbol.get(symbol);
            if (closes == null || closes.size() < 2) continue;   // a line needs at least two points
            series.put(position.getTicker(), new PriceSeries(closes, changePercent(closes)));
        }
        return series;
    }

    private BigDecimal changePercent(List<BigDecimal> closes) {
        BigDecimal first = closes.getFirst();
        if (first.signum() == 0) return BigDecimal.ZERO;
        return closes.getLast().subtract(first).multiply(BigDecimal.valueOf(100)).divide(first, 2, RoundingMode.HALF_UP);
    }

}

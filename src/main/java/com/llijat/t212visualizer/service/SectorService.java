package com.llijat.t212visualizer.service;

import com.llijat.t212visualizer.dto.HoldingMove;
import com.llijat.t212visualizer.dto.SectorHolding;
import com.llijat.t212visualizer.dto.SectorMap;
import com.llijat.t212visualizer.dto.SectorTile;
import com.llijat.t212visualizer.dto.Subsector;
import com.llijat.t212visualizer.dto.SubsectorTile;
import com.llijat.t212visualizer.model.Position;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class SectorService {
    private static final BigDecimal HUNDRED = new BigDecimal("100");

    private final PositionService positionService;
    private final QuoteService quoteService;
    private final CompanyProfileService companyProfileService;

    public SectorService(PositionService positionService, QuoteService quoteService, CompanyProfileService companyProfileService) {
        this.positionService = positionService;
        this.quoteService = quoteService;
        this.companyProfileService = companyProfileService;
    }

    public SectorMap getSectorMap() {
        // 1. Starting data
        List<Position> positions = positionService.getAllPositions().stream()
                .filter(position -> position.getCurrentValue() != null)
                .toList();
        BigDecimal invested = positions.stream()
                .map(Position::getCurrentValue)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        Map<String, String> symbols = companyProfileService.currentSymbols();
        Map<String, String> industries = companyProfileService.currentIndustries();
        Map<String, BigDecimal> moveByTicker = new HashMap<>();
        for (HoldingMove move : quoteService.getMoves()) {
            moveByTicker.put(move.ticker(), move.changePercent());
        }

        // 2. My stocks, grouped by subsector
        Map<String, List<SectorHolding>> holdingsBySubsector = new HashMap<>();
        for (Position position : positions) {
            String ticker = position.getTicker();
            String symbol = symbols.getOrDefault(ticker, position.getSymbol());
            Subsector subsector = SectorCatalog.subsectorOf(symbol, industries.get(ticker));
            SectorHolding holding = new SectorHolding(ticker, symbol, position.getName(),
                    position.getCurrentValue(), percentOf(position.getCurrentValue(), invested), moveByTicker.get(ticker));
            holdingsBySubsector.computeIfAbsent(subsector.name(), name -> new ArrayList<>()).add(holding);
        }

        // 3. One tile per subsector of the catalog, grouped by sector in the catalog's order
        Map<String, BigDecimal> etfMoves = quoteService.getSectorEtfMoves();
        Map<String, List<SubsectorTile>> tilesBySector = new LinkedHashMap<>();
        for (Subsector subsector : SectorCatalog.all()) {
            List<SectorHolding> holdings = holdingsBySubsector.getOrDefault(subsector.name(), List.of());
            SubsectorTile tile = subsectorTile(subsector, etfMoves.get(subsector.etf()), holdings, invested);
            tilesBySector.computeIfAbsent(subsector.sector(), sector -> new ArrayList<>()).add(tile);
        }

        // 4. "Other" only shows up if one of my stocks fell there
        List<SectorHolding> others = holdingsBySubsector.get(SectorCatalog.OTHER.name());
        if (others != null) {
            tilesBySector.put(SectorCatalog.OTHER.sector(), List.of(subsectorTile(SectorCatalog.OTHER, null, others, invested)));
        }

        List<SectorTile> sectors = new ArrayList<>();
        tilesBySector.forEach((sector, tiles) -> sectors.add(sectorTile(sector, tiles)));
        return new SectorMap(sectors, invested);
    }

    private static SubsectorTile subsectorTile(Subsector subsector, BigDecimal changePercent, List<SectorHolding> holdings, BigDecimal invested) {
        BigDecimal myValue = BigDecimal.ZERO;
        BigDecimal myTodayChange = BigDecimal.ZERO;
        for (SectorHolding holding : holdings) {
            myValue = myValue.add(holding.value());
            myTodayChange = myTodayChange.add(todayChange(holding));
        }
        List<SectorHolding> biggestFirst = holdings.stream()
                .sorted(Comparator.comparing(SectorHolding::value).reversed())
                .toList();
        return new SubsectorTile(subsector.name(), subsector.etf(), changePercent, subsector.marketWeight(),
                myValue, percentOf(myValue, invested), myTodayChange, biggestFirst);
    }

    private static SectorTile sectorTile(String name, List<SubsectorTile> tiles) {
        BigDecimal marketWeight = BigDecimal.ZERO;
        BigDecimal myValue = BigDecimal.ZERO;
        BigDecimal weightedChange = BigDecimal.ZERO;
        BigDecimal weightWithChange = BigDecimal.ZERO;
        for (SubsectorTile tile : tiles) {
            marketWeight = marketWeight.add(tile.marketWeight());
            myValue = myValue.add(tile.myValue());
            if (tile.changePercent() != null) {
                weightedChange = weightedChange.add(tile.changePercent().multiply(tile.marketWeight()));
                weightWithChange = weightWithChange.add(tile.marketWeight());
            }
        }
        BigDecimal changePercent = weightWithChange.signum() == 0 ? null : weightedChange.divide(weightWithChange, 2, RoundingMode.HALF_UP);
        return new SectorTile(name, changePercent, marketWeight, myValue, tiles);
    }

    // Today's move in euros, from today's % and the value now: value × % / (100 + %)
    private static BigDecimal todayChange(SectorHolding holding) {
        BigDecimal percent = holding.changePercent();
        if (percent == null) return BigDecimal.ZERO;
        return holding.value().multiply(percent).divide(HUNDRED.add(percent), 2, RoundingMode.HALF_UP);
    }

    private static BigDecimal percentOf(BigDecimal part, BigDecimal total) {
        if (total.signum() == 0) return BigDecimal.ZERO;
        return part.multiply(HUNDRED).divide(total, 1, RoundingMode.HALF_UP);
    }
}
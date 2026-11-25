package com.llijat.t212visualizer.dto;

import java.math.BigDecimal;
import java.util.List;

public record SubsectorTile(
        String name,
        String etf,
        BigDecimal changePercent,
        BigDecimal marketWeight,
        BigDecimal myValue,
        BigDecimal myWeight,
        BigDecimal myTodayChange,
        List<SectorHolding> holdings
) {}

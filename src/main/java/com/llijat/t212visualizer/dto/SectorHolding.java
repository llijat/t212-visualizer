package com.llijat.t212visualizer.dto;

import java.math.BigDecimal;

public record SectorHolding(
        String ticker,
        String symbol,
        String name,
        BigDecimal value,
        BigDecimal weight,
        BigDecimal changePercent
) {}

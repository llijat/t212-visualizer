package com.llijat.t212visualizer.dto;

import java.math.BigDecimal;

public record HoldingMove(
        String symbol,
        String ticker,
        BigDecimal changePercent,
        BigDecimal weightPercent
) {
}

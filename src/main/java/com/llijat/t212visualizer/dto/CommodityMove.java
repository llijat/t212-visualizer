package com.llijat.t212visualizer.dto;

import java.math.BigDecimal;

public record CommodityMove(
        String symbol,
        BigDecimal changePercent
) {
    public String name() {
        return switch (symbol) {
            case "GLD" -> "Gold";
            case "SLV" -> "Silver";
            case "USO" -> "Crude oil";
            case "BINANCE:BTCUSDT" -> "Bitcoin";
            default -> symbol;
        };
    }
}

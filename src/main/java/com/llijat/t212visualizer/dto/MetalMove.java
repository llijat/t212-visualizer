package com.llijat.t212visualizer.dto;

import java.math.BigDecimal;

public record MetalMove(
        String etf,
        BigDecimal changePercent
) {
    public String name() {
        return switch (etf) {
            case "GLD" -> "Gold";
            case "SLV" -> "Silver";
            default -> etf;
        };
    }
}

package com.llijat.t212visualizer.dto;

import java.math.BigDecimal;

public record Subsector(
        String name,
        String sector,
        String etf,
        BigDecimal marketWeight
) {
}

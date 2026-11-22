package com.llijat.t212visualizer.dto;

import java.math.BigDecimal;

public record DailyChange(
        BigDecimal amount,
        BigDecimal percent
) {
}

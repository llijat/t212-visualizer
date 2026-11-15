package com.llijat.t212visualizer.dto;

import java.math.BigDecimal;
import java.math.RoundingMode;

public record BudgetProgress(
        String category,
        BigDecimal limit,
        BigDecimal spent
) {
    public boolean isOver() {
        return spent.compareTo(limit) > 0;
    }

    public int percent() {
        return spent.multiply(BigDecimal.valueOf(100))
                .divide(limit, 0, RoundingMode.HALF_UP)
                .intValue();
    }
}

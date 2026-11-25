package com.llijat.t212visualizer.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record AccountPoint(
        LocalDate date,
        BigDecimal value,
        BigDecimal netDeposits,
        BigDecimal growth
) {
}

package com.llijat.t212visualizer.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record DailySpending(
        LocalDate date,
        BigDecimal total
) {}

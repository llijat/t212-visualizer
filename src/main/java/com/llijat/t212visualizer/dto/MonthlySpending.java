package com.llijat.t212visualizer.dto;


import java.math.BigDecimal;
import java.time.YearMonth;

public record MonthlySpending(
        YearMonth month,
        BigDecimal total
) {}
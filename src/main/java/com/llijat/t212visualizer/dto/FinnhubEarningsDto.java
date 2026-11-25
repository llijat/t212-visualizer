package com.llijat.t212visualizer.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record FinnhubEarningsDto (
        String symbol,
        LocalDate date,
        String hour,
        int quarter,
        int year,
        BigDecimal epsEstimate,
        BigDecimal epsActual,
        BigDecimal revenueEstimate,
        BigDecimal revenueActual
) {}
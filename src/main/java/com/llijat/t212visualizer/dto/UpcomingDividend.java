package com.llijat.t212visualizer.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record UpcomingDividend(
        String symbol,
        String ticker,
        LocalDate date,
        BigDecimal receivedEur,
        BigDecimal estimatedEur
) {
}
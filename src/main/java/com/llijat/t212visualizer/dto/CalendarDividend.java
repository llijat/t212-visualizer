package com.llijat.t212visualizer.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record CalendarDividend(
        String ticker,
        LocalDate paymentDate,
        LocalDate exDividendDate,
        BigDecimal amountPerShare,
        BigDecimal receivedEur,
        BigDecimal estimatedEur
) {
    public boolean isReceived() {
        return receivedEur != null;
    }
}
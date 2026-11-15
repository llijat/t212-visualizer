package com.llijat.t212visualizer.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record Subscription(
        String merchant,
        BigDecimal amount,
        BigDecimal previousAmount,
        LocalDate lastPayment,
        LocalDate nextPayment
) {
    public boolean priceChanged() {
        return amount.compareTo(previousAmount) != 0;
    }
}

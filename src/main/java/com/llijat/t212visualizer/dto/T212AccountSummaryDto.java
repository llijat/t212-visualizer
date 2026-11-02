package com.llijat.t212visualizer.dto;

import java.math.BigDecimal;

public record T212AccountSummaryDto(
        String currency,
        long id,
        BigDecimal totalValue,
        Cash cash,
        Investments investments
) {
    public record Cash(
            BigDecimal availableToTrade,
            BigDecimal inPies,
            BigDecimal reservedForOrders
    ) {}
    public record Investments(
            BigDecimal currentValue,
            BigDecimal totalCost,
            BigDecimal realizedProfitLoss,
            BigDecimal unrealizedProfitLoss
    ) {}
}

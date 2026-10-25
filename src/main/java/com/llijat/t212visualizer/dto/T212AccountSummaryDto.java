package com.llijat.t212visualizer.dto;

public record T212AccountSummaryDto(
        String currency,
        long id,
        double totalValue,
        Cash cash,
        Investments investments
) {
    public record Cash(
            double availableToTrade,
            double inPies,
            double reservedForOrders
    ) {}
    public record Investments(
            double currentValue,
            double totalCost,
            double realizedProfitLoss,
            double unrealizedProfitLoss
    ) {}
}

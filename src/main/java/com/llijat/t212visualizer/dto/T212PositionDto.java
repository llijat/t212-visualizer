package com.llijat.t212visualizer.dto;

/**
 * Matches the confirmed response shape of GET /equity/positions
 * (docs.trading212.com/api/positions/getpositions.md). Nested, not flat —
 * ticker lives under `instrument`, P/L under `walletImpact`.
 */
public record T212PositionDto(
        double averagePricePaid,
        String createdAt,
        double currentPrice,
        double quantity,
        double quantityAvailableForTrading,
        double quantityInPies,
        Instrument instrument,
        WalletImpact walletImpact) {
    public record Instrument(
            String currency,
            String isin,
            String name,
            String ticker
    ) {}

    public record WalletImpact(
            String currency,
            double currentValue,
            Double fxImpact, // nullable — only set when instrument currency != account currency
            double totalCost,
            double unrealizedProfitLoss
    ) {
    }
}

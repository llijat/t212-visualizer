package com.llijat.t212visualizer.dto;

import java.math.BigDecimal;

/**
 * Matches the confirmed response shape of GET /equity/positions
 * (docs.trading212.com/api/positions/getpositions.md). Nested, not flat —
 * ticker lives under `instrument`, P/L under `walletImpact`.
 */
public record T212PositionDto(
        BigDecimal averagePricePaid,
        String createdAt,
        BigDecimal currentPrice,
        BigDecimal quantity,
        BigDecimal quantityAvailableForTrading,
        BigDecimal quantityInPies,
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
            BigDecimal currentValue,
            BigDecimal fxImpact, // nullable — only set when instrument currency != account currency
            BigDecimal totalCost,
            BigDecimal unrealizedProfitLoss
    ) {
    }
}

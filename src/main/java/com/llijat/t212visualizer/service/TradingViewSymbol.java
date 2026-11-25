package com.llijat.t212visualizer.service;

import java.util.Map;

public class TradingViewSymbol {
    private static final Map<String, String> EXCHANGES = Map.of(
            "UW", "NASDAQ", "UQ", "NASDAQ", "UR", "NASDAQ",
            "UN", "NYSE",
            "UA", "AMEX", "UP", "AMEX",   // NYSE American and NYSE Arca: TradingView calls both AMEX
            "GY", "XETR",
            "LN", "LSE",
            "SW", "SIX", "SE", "SIX");

    private TradingViewSymbol() {}

    public static String of(String exchange, String symbol) {
        if (symbol == null) return null;
        String clean = symbol.replace('/', '.');
        String market = exchange == null ? null : EXCHANGES.get(exchange);   // null: an exchange TradingView should pick itself
        return market == null ? clean : market + ":" + clean;
    }
}

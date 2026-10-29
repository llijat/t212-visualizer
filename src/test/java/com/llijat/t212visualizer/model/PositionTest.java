package com.llijat.t212visualizer.model;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;


class PositionTest {
    private Position positionWithTicker(String ticker) {
        return new Position(ticker, "Test Inc.", 1, 100, 110, 110, 10, null, 100, "USD", "EUR");
    }

    private Position positionWithPl(double unrealizedProfitLoss, double totalCost) {
        return new Position("TEST_US_EQ", "Test Inc.", 1, 100, 110, 110,
                unrealizedProfitLoss, null, totalCost, "USD", "EUR");
    }

    @Test
    void getSymbol_removesExchangeSuffix() {
        Position position = positionWithTicker("AAPL_US_EQ");

        String symbol = position.getSymbol();

        assertEquals("AAPL", symbol);
    }

    @Test
    void getSymbol_withoutUnderscore_returnsTickerUnchanged() {
        Position position = positionWithTicker("AAPL");

        String symbol = position.getSymbol();

        assertEquals("AAPL", symbol);
    }

    @Test
    void getUnrealizedPlPercent_withProfit() {
        Position position = positionWithPl(10, 100);

        double percent = position.getUnrealizedPlPercent();

        assertEquals(10.0, percent, 0.0001);
    }

    @Test
    void getUnrealizedPlPercent_withLoss() {
        Position position = positionWithPl(-25, 100);

        double percent = position.getUnrealizedPlPercent();

        assertEquals(-25.0, percent, 0.0001);
    }

    @Test
    void getUnrealizedPlPercent_withZeroCost_returnsZero() {
        Position position = positionWithPl(10, 0);

        double percent = position.getUnrealizedPlPercent();

        assertEquals(0.0, percent, 0.0001);
    }
}

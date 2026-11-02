package com.llijat.t212visualizer.model;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;


class PositionTest {
    private Position positionWithTicker(String ticker) {
        return new Position(ticker, "Test Inc.", new BigDecimal("1"), new BigDecimal("100"),
                new BigDecimal("110"), new BigDecimal("110"), new BigDecimal("10"), null,
                new BigDecimal("100"), "USD", "EUR");
    }

    private Position positionWithPl(String unrealizedProfitLoss, String totalCost) {
        return new Position("TEST_US_EQ", "Test Inc.", new BigDecimal("1"), new BigDecimal("100"),
                new BigDecimal("110"), new BigDecimal("110"), new BigDecimal(unrealizedProfitLoss), null,
                new BigDecimal(totalCost), "USD", "EUR");
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
        Position position = positionWithPl("10", "100");

        BigDecimal percent = position.getUnrealizedPlPercent();

        assertEquals(new BigDecimal("10.00"), percent);
    }

    @Test
    void getUnrealizedPlPercent_withLoss() {
        Position position = positionWithPl("-25", "100");

        BigDecimal percent = position.getUnrealizedPlPercent();

        assertEquals(new BigDecimal("-25.00"), percent);
    }

    @Test
    void getUnrealizedPlPercent_withZeroCost_returnsZero() {
        // "0.0000" es como llega de la base de datos: equals("0") no lo detectaría, signum() sí
        Position position = positionWithPl("10", "0.0000");

        BigDecimal percent = position.getUnrealizedPlPercent();

        assertEquals(BigDecimal.ZERO, percent);
    }

    @Test
    void getUnrealizedPlPercent_withEndlessDecimals_roundsToTwo() {
        // 1 / 3 no termina nunca: sin indicar decimales, divide() lanzaría una excepción
        Position position = positionWithPl("1", "3");

        BigDecimal percent = position.getUnrealizedPlPercent();

        assertEquals(new BigDecimal("33.33"), percent);
    }
}

package com.llijat.t212visualizer.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/** OpenFIGI's exchange codes, turned into the names TradingView puts in front of a symbol. */
class TradingViewSymbolTest {

    @Test
    void theThreeNasdaqTiers_areAllNasdaq() {
        assertEquals("NASDAQ:AAPL", TradingViewSymbol.of("UW", "AAPL"));
        assertEquals("NASDAQ:SOFI", TradingViewSymbol.of("UQ", "SOFI"));
        assertEquals("NASDAQ:ABCD", TradingViewSymbol.of("UR", "ABCD"));
    }

    @Test
    void nyseAndItsTwoSmallerMarkets() {
        assertEquals("NYSE:KO", TradingViewSymbol.of("UN", "KO"));
        assertEquals("AMEX:SPY", TradingViewSymbol.of("UP", "SPY"));   // NYSE Arca: TradingView calls it AMEX
        assertEquals("AMEX:UEC", TradingViewSymbol.of("UA", "UEC"));
    }

    @Test
    void theEuropeanExchangesOfT212() {
        assertEquals("XETR:RWE", TradingViewSymbol.of("GY", "RWE"));
        assertEquals("LSE:VUSA", TradingViewSymbol.of("LN", "VUSA"));
        assertEquals("SIX:NESN", TradingViewSymbol.of("SW", "NESN"));
    }

    @Test
    void aShareClass_usesADot_asTradingViewWritesIt() {
        assertEquals("NYSE:BRK.B", TradingViewSymbol.of("UN", "BRK/B"));
    }

    @Test
    void anExchangeItDoesNotKnow_orNone_leavesTheSymbolAlone_forTradingViewToPick() {
        assertEquals("ASML", TradingViewSymbol.of("NA", "ASML"));
        assertEquals("ASML", TradingViewSymbol.of(null, "ASML"));   // Map.of would throw on a null key
    }

    @Test
    void withoutASymbol_thereIsNothing() {
        assertNull(TradingViewSymbol.of("UW", null));
    }
}

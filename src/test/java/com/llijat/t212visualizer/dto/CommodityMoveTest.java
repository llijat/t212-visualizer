package com.llijat.t212visualizer.dto;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CommodityMoveTest {

    @Test
    void gld_isGold() {
        assertEquals("Gold", new CommodityMove("GLD", BigDecimal.ONE).name());
    }

    @Test
    void slv_isSilver() {
        assertEquals("Silver", new CommodityMove("SLV", BigDecimal.ONE).name());
    }

    @Test
    void uso_isCrudeOil_andTheBinancePair_isBitcoin() {
        assertEquals("Crude oil", new CommodityMove("USO", BigDecimal.ONE).name());
        assertEquals("Bitcoin", new CommodityMove("BINANCE:BTCUSDT", BigDecimal.ONE).name());
    }

    @Test
    void anotherEtf_keepsItsOwnSymbol_insteadOfAWrongName() {
        assertEquals("PPLT", new CommodityMove("PPLT", BigDecimal.ONE).name());
    }
}

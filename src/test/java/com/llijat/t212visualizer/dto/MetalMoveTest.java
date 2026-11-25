package com.llijat.t212visualizer.dto;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MetalMoveTest {

    @Test
    void gld_isGold() {
        assertEquals("Gold", new MetalMove("GLD", BigDecimal.ONE).name());
    }

    @Test
    void slv_isSilver() {
        assertEquals("Silver", new MetalMove("SLV", BigDecimal.ONE).name());
    }

    @Test
    void anotherEtf_keepsItsOwnSymbol_insteadOfAWrongName() {
        assertEquals("PPLT", new MetalMove("PPLT", BigDecimal.ONE).name());
    }
}

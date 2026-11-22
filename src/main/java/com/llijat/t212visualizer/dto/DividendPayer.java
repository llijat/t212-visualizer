package com.llijat.t212visualizer.dto;

import java.math.BigDecimal;

public record DividendPayer (
        String ticker,
        BigDecimal receivedEur
) {
    public String symbol() {
        int underscore = ticker.indexOf("_");
        if (underscore == -1) return ticker;
        return ticker.substring(0, underscore);
    }
}

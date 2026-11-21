package com.llijat.t212visualizer.dto;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;
import java.time.LocalDate;


public record AlphaVantageDividendDto(
        @JsonProperty("ex_dividend_date") String exDividendDate,
        @JsonProperty("payment_date") String paymentDate,
        String amount
) {
    public LocalDate paymentDay() {
        return toDate(paymentDate);
    }

    public LocalDate exDividendDay() {
        return toDate(exDividendDate);
    }

    public BigDecimal amountPerShare() {
        if (amount == null || amount.equals("None")) return null;
        return new BigDecimal(amount);
    }

    private static LocalDate toDate(String text) {
        if (text == null || text.equals("None")) return null;
        return LocalDate.parse(text);
    }
}

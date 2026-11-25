package com.llijat.t212visualizer.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;

public record T212DividendDto(
        String ticker,
        BigDecimal quantity,
        BigDecimal grossAmountPerShare,
        BigDecimal amountInEuro,
        String paidOn
) {
    public LocalDate paidDay() {
        return OffsetDateTime.parse(paidOn).toLocalDate();
    }


}
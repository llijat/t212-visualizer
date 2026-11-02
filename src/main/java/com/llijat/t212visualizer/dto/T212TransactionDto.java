package com.llijat.t212visualizer.dto;

import java.math.BigDecimal;

public record T212TransactionDto (
        BigDecimal amount,
        String currency,
        String dateTime,
        String reference,
        String type
) {}

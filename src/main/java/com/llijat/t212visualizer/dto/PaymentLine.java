package com.llijat.t212visualizer.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record PaymentLine(
        LocalDate date,
        String merchant,
        String category,
        BigDecimal amount,
        boolean corrected)
{}
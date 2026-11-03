package com.llijat.t212visualizer.dto;

import java.math.BigDecimal;

public record CategorySpending(
        String category,
        BigDecimal total,
        long payments
) {}

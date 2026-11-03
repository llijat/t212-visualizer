package com.llijat.t212visualizer.dto;

import java.math.BigDecimal;

public record MerchantSpending(
        String name,
        BigDecimal total,
        long payments
) {}
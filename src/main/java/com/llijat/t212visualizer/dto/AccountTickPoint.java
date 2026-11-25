package com.llijat.t212visualizer.dto;

import java.math.BigDecimal;
import java.time.Instant;

public record AccountTickPoint(
        Instant takenAt,
        BigDecimal value,
        BigDecimal netDeposits,
        BigDecimal growth
) {}

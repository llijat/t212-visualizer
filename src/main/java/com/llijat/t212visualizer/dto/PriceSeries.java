package com.llijat.t212visualizer.dto;

import java.math.BigDecimal;
import java.util.List;

public record PriceSeries(
        List<BigDecimal> closes,
        BigDecimal changePercent
) {}

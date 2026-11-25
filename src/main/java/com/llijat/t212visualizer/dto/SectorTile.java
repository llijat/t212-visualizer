package com.llijat.t212visualizer.dto;

import java.math.BigDecimal;
import java.util.List;

public record SectorTile(
        String name,
        BigDecimal changePercent,
        BigDecimal marketWeight,
        BigDecimal myValue,
        List<SubsectorTile> subsectors
) {}

package com.llijat.t212visualizer.dto;

import java.util.List;

public record AlphaVantageDividendsDto(
        String symbol,
        List<AlphaVantageDividendDto> data
) {}

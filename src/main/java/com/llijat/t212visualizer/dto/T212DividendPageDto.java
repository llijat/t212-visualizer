package com.llijat.t212visualizer.dto;

import java.util.List;

public record T212DividendPageDto(
        List<T212DividendDto> items,
        String nextPagePath
) {}
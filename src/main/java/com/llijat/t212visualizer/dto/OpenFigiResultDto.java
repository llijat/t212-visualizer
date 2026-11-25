package com.llijat.t212visualizer.dto;

import java.util.List;

public record OpenFigiResultDto(
        List<OpenFigiListingDto> data,
        String warning
) {}

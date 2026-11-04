package com.llijat.t212visualizer.dto;

public record T212ExportRequestDto (
        DataIncluded dataIncluded,
        String timeFrom,
        String timeTo
) {
    public record DataIncluded(
            boolean includeDividends,
            boolean includeInterest,
            boolean includeOrders,
            boolean includeTransactions
    ) {}
}

package com.llijat.t212visualizer.dto;

import java.time.LocalDate;

public record UpcomingEarnings(
        String symbol,
        String ticker,
        LocalDate date,
        String hour
)
{
    public String hourLabel() {
        if (hour == null) return "";
        return switch (hour) {
            case "bmo" -> "before the open";
            case "amc" -> "after the close";
            case "dmh" -> "during the session";
            default -> "";
        };
    }
}

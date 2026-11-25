package com.llijat.t212visualizer.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;

public record AlphaVantageDailyDto(
        @JsonProperty("Time Series (Daily)")Map<String, Map<String, String>> days
) {
    public Map<LocalDate, BigDecimal> closes() {
        Map<LocalDate, BigDecimal> close = new HashMap<>();
        for (Map.Entry<String, Map<String, String>> entry : days.entrySet()) {
            close.put(LocalDate.parse(entry.getKey()), new BigDecimal(entry.getValue().get("4. close")));
        }
        return close;
    }
}

package com.llijat.t212visualizer.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;

public record FinnhubQuoteDto(
        @JsonProperty("c") BigDecimal price,
        @JsonProperty("dp") BigDecimal changePercent,
        @JsonProperty("t") long time)
{
    public boolean isKnown() {
        return changePercent != null && time > 0;
    }
}
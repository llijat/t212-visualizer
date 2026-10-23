package com.llijat.t212visualizer.dto;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;

public record FinnhubNewsDto (
    String category,
    long datetime,
    String headline,
    String url,
    long id,
    String related,
    String image,
    String summary)
{
    public LocalDateTime publishedAt() {
        return LocalDateTime.ofInstant(Instant.ofEpochSecond(datetime), ZoneId.systemDefault());
    }
}
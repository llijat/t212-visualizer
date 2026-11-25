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
    String summary,
    String source)
{
    /** The time it came out where the app is used. The zone is passed in: a record cannot receive the app's Clock */
    public LocalDateTime publishedAt(ZoneId zone) {
        return LocalDateTime.ofInstant(Instant.ofEpochSecond(datetime), zone);
    }
}
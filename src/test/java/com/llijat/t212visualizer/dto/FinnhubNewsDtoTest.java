package com.llijat.t212visualizer.dto;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.time.ZoneId;

import static org.junit.jupiter.api.Assertions.assertEquals;

class FinnhubNewsDtoTest {

    // One item as Finnhub's /company-news sends it (field names copied from its documentation)
    private static final String JSON = """
            {"category": "company", "datetime": 1794871200, "headline": "Acme reports quarterly results",
             "id": 7, "image": "", "related": "ACME", "source": "Sample Wire",
             "summary": "Revenue grew.", "url": "https://example.com/acme"}
            """;

    @Test
    void theSource_isReadFromTheJson() throws Exception {
        FinnhubNewsDto news = new ObjectMapper().readValue(JSON, FinnhubNewsDto.class);

        assertEquals("Sample Wire", news.source());
        assertEquals("ACME", news.related());
    }

    @Test
    void theTime_isShownInSpain_whateverTheComputerSays() throws Exception {
        // 1794871200 is 2026-11-16 at 23:20 UTC: in Spain (UTC+1 in November) it is already the 17th
        FinnhubNewsDto news = new ObjectMapper().readValue(JSON, FinnhubNewsDto.class);

        assertEquals(LocalDateTime.of(2026, 11, 17, 0, 20), news.publishedAt(ZoneId.of("Europe/Madrid")));
    }
}

package com.llijat.t212visualizer.model;

import java.time.ZoneId;

import com.llijat.t212visualizer.dto.FinnhubNewsDto;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NewsItemTest {

    // 1794871200 is 2026-11-16 at 23:20 UTC: in Spain it is already the 17th at 00:20
    private static final FinnhubNewsDto FROM_FINNHUB = new FinnhubNewsDto("company", 1794871200L,
            "Acme reports quarterly results", "https://example.com/acme", 7L, "ACME",
            "https://example.com/acme.png", "Revenue grew.", "Sample Wire");

    @Test
    void theMoment_isStoredAsAnInstant() {
        NewsItem item = new NewsItem(FROM_FINNHUB, NewsFeed.PORTFOLIO);

        assertEquals(Instant.parse("2026-11-16T23:20:00Z"), item.getPublishedAt());
        assertEquals(7L, item.getId());
        assertEquals(NewsFeed.PORTFOLIO, item.getFeed());
    }

    @Test
    void goingThereAndBack_keepsTheSameTime() {
        FinnhubNewsDto back = new NewsItem(FROM_FINNHUB, NewsFeed.PORTFOLIO).toDto();

        assertEquals(FROM_FINNHUB.datetime(), back.datetime());
        assertEquals(LocalDateTime.of(2026, 11, 17, 0, 20), back.publishedAt(ZoneId.of("Europe/Madrid")));
    }

    @Test
    void goingThereAndBack_keepsWhatThePagesShow() {
        FinnhubNewsDto back = new NewsItem(FROM_FINNHUB, NewsFeed.PORTFOLIO).toDto();

        assertEquals(FROM_FINNHUB.id(), back.id());
        assertEquals(FROM_FINNHUB.headline(), back.headline());
        assertEquals(FROM_FINNHUB.url(), back.url());
        assertEquals(FROM_FINNHUB.related(), back.related());
        assertEquals(FROM_FINNHUB.summary(), back.summary());
        assertEquals(FROM_FINNHUB.source(), back.source());
    }

    @Test
    void theImage_isNotKept_becauseThePagesNeverLoadThirdPartyImages() {
        FinnhubNewsDto back = new NewsItem(FROM_FINNHUB, NewsFeed.PORTFOLIO).toDto();

        assertEquals("", back.image());
    }

    @Test
    void aNewNews_isBornWithoutAStar() {
        assertFalse(new NewsItem(FROM_FINNHUB, NewsFeed.MARKET).isStarred());
    }

    @Test
    void starringTwice_keepsTheStar_becauseItSaysTheStateNotFlipsIt() {
        // A double click sends the same request twice: it must not take the star away again
        NewsItem item = new NewsItem(FROM_FINNHUB, NewsFeed.MARKET);

        item.changeStarred(true);
        item.changeStarred(true);

        assertTrue(item.isStarred());
    }

    @Test
    void theStar_canBeTakenAway() {
        NewsItem item = new NewsItem(FROM_FINNHUB, NewsFeed.MARKET);

        item.changeStarred(true);
        item.changeStarred(false);

        assertFalse(item.isStarred());
    }
}

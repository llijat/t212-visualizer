package com.llijat.t212visualizer.service;

import java.time.ZoneId;

import com.llijat.t212visualizer.dto.FinnhubNewsDto;
import com.llijat.t212visualizer.dto.NewsDay;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Written BEFORE the code (TDD): these tests are the specification of NewsGrouper.byDay.
 * The rules: one NewsDay per day in Spain, the newest day first, and inside each day the newest news first.
 */
class NewsGrouperTest {

    private static final ZoneId MADRID = ZoneId.of("Europe/Madrid");

    // A news item published at that moment (UTC, as Finnhub sends it); the id tells them apart
    private static FinnhubNewsDto news(long id, String utc) {
        long datetime = Instant.parse(utc).getEpochSecond();
        return new FinnhubNewsDto("company", datetime, "Headline " + id, "https://example.com/" + id,
                id, "ACME", "", "Summary " + id, "Sample Wire");
    }

    private static List<Long> ids(NewsDay day) {
        return day.news().stream().map(FinnhubNewsDto::id).toList();
    }

    @Test
    void newsOfTheSameDay_goTogether() {
        List<NewsDay> days = NewsGrouper.byDay(List.of(
                news(1, "2026-11-16T09:00:00Z"),
                news(2, "2026-11-16T15:00:00Z")), MADRID);

        assertEquals(1, days.size());
        assertEquals(LocalDate.of(2026, 11, 16), days.get(0).date());
        assertEquals(2, days.get(0).news().size());
    }

    @Test
    void theNewestDay_comesFirst() {
        List<NewsDay> days = NewsGrouper.byDay(List.of(
                news(1, "2026-11-16T10:00:00Z"),
                news(2, "2026-11-14T10:00:00Z"),
                news(3, "2026-11-15T10:00:00Z")), MADRID);

        assertEquals(List.of(LocalDate.of(2026, 11, 16), LocalDate.of(2026, 11, 15), LocalDate.of(2026, 11, 14)),
                days.stream().map(NewsDay::date).toList());
    }

    @Test
    void insideADay_theNewestNewsComesFirst() {
        List<NewsDay> days = NewsGrouper.byDay(List.of(
                news(1, "2026-11-16T08:00:00Z"),
                news(2, "2026-11-16T18:00:00Z"),
                news(3, "2026-11-16T12:00:00Z")), MADRID);

        assertEquals(List.of(2L, 3L, 1L), ids(days.get(0)));
    }

    @Test
    void newsInAnyOrder_endUpInOrder() {
        // Finnhub sends each company newest first, but several companies together are mixed
        List<NewsDay> days = NewsGrouper.byDay(List.of(
                news(1, "2026-11-14T09:00:00Z"),
                news(2, "2026-11-16T20:00:00Z"),
                news(3, "2026-11-15T07:00:00Z"),
                news(4, "2026-11-16T06:00:00Z"),
                news(5, "2026-11-14T22:00:00Z")), MADRID);

        assertEquals(3, days.size());
        assertEquals(List.of(2L, 4L), ids(days.get(0)));
        assertEquals(List.of(3L), ids(days.get(1)));
        assertEquals(List.of(5L, 1L), ids(days.get(2)));
    }

    @Test
    void theDayChanges_atMidnightInSpain_notAtMidnightUtc() {
        // 23:30 UTC on the 16th is 00:30 on the 17th in Spain; 22:30 UTC is still 23:30 on the 16th
        List<NewsDay> days = NewsGrouper.byDay(List.of(
                news(1, "2026-11-16T23:30:00Z"),
                news(2, "2026-11-16T22:30:00Z")), MADRID);

        assertEquals(2, days.size());
        assertEquals(LocalDate.of(2026, 11, 17), days.get(0).date());
        assertEquals(List.of(1L), ids(days.get(0)));
        assertEquals(LocalDate.of(2026, 11, 16), days.get(1).date());
        assertEquals(List.of(2L), ids(days.get(1)));
    }

    @Test
    void noNews_noDays() {
        assertTrue(NewsGrouper.byDay(List.of(), MADRID).isEmpty());
    }

    @Test
    void noNewsIsLost() {
        List<FinnhubNewsDto> news = List.of(
                news(1, "2026-11-10T10:00:00Z"), news(2, "2026-11-11T10:00:00Z"), news(3, "2026-11-11T11:00:00Z"),
                news(4, "2026-11-13T10:00:00Z"), news(5, "2026-11-16T10:00:00Z"), news(6, "2026-11-16T12:00:00Z"));

        int grouped = NewsGrouper.byDay(news, MADRID).stream().mapToInt(day -> day.news().size()).sum();

        assertEquals(6, grouped);
    }
}

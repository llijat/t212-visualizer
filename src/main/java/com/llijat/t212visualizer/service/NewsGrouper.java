package com.llijat.t212visualizer.service;

import com.llijat.t212visualizer.dto.FinnhubNewsDto;
import com.llijat.t212visualizer.dto.NewsDay;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.*;

public class NewsGrouper {
    private NewsGrouper() {}

    public static List<NewsDay> byDay(List<FinnhubNewsDto> news, ZoneId zone) {
        List<FinnhubNewsDto> sorted = news.stream().sorted(Comparator.comparingLong(FinnhubNewsDto::datetime).reversed()).toList();
        Map<LocalDate, List<FinnhubNewsDto>> newsByDay = new LinkedHashMap<>();

        for (FinnhubNewsDto f : sorted) {
            newsByDay.computeIfAbsent(f.publishedAt(zone).toLocalDate(), day -> new ArrayList<>()).add(f);
        }

        List<NewsDay> list = new ArrayList<>();
        for (Map.Entry<LocalDate, List<FinnhubNewsDto>> entry : newsByDay.entrySet()) {
            NewsDay n = new NewsDay(entry.getKey(), entry.getValue());
            list.add(n);
        }

        return list;
    }
}

package com.llijat.t212visualizer.service;

import com.llijat.t212visualizer.dto.FinnhubNewsDto;
import com.llijat.t212visualizer.dto.NewsDay;

import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

public class NewsGrouper {
    private NewsService newsService;

    private NewsGrouper() {}

    public static List<NewsDay> byDay(List<FinnhubNewsDto> news) {
        List<FinnhubNewsDto> sorted = news.stream().sorted(Comparator.comparingLong(FinnhubNewsDto::datetime).reversed()).toList();
        Map<LocalDate, List<FinnhubNewsDto>> newsByDay= new LinkedHashMap<>();

        for (FinnhubNewsDto f : sorted) {
            newsByDay.computeIfAbsent(f.publishedAt().toLocalDate(), day -> new ArrayList<>()).add(f);
        }

        List<NewsDay> list = new ArrayList<>();
        for (Map.Entry<LocalDate, List<FinnhubNewsDto>> entry : newsByDay.entrySet()) {
            NewsDay n = new NewsDay(entry.getKey(), entry.getValue());
            list.add(n);
        }

        return list;
    }

    public Map<String, Long> getNewsCountBySymbol() {
        return newsService.getPortfolioNews().stream()
                .filter(news -> news.related() != null && !news.related().isBlank())
                .collect(Collectors.groupingBy(FinnhubNewsDto::related, TreeMap::new, Collectors.counting()));
    }

}

package com.llijat.t212visualizer.controller;

import com.llijat.t212visualizer.service.NewsService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import java.time.LocalDate;
import java.time.ZoneId;

@Controller
public class NewsController {
    private static final ZoneId SPAIN = ZoneId.of("Europe/Madrid");

    private final NewsService newsService;

    public NewsController(NewsService newsService) {
        this.newsService = newsService;
    }

    @GetMapping("/news")
    public String news(Model model) {
        model.addAttribute("portfolioDays", newsService.getPortfolioNewsByDay());
        model.addAttribute("marketDays", newsService.getMarketNewsByDay());
        model.addAttribute("newsCount", newsService.getNewsCountBySymbol());
        model.addAttribute("tickers", newsService.getTickerBySymbol());
        model.addAttribute("today", LocalDate.now(SPAIN));
        return "news";
    }

}

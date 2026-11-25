package com.llijat.t212visualizer.controller;

import com.llijat.t212visualizer.service.CalendarService;
import com.llijat.t212visualizer.service.NewsService;
import com.llijat.t212visualizer.service.PriceHistoryService;
import com.llijat.t212visualizer.service.QuoteService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.time.Clock;
import java.time.LocalDate;

@Controller
public class NewsController {
    private final Clock clock;

    private final NewsService newsService;
    private final QuoteService quoteService;
    private final CalendarService calendarService;
    private final PriceHistoryService priceHistoryService;

    public NewsController(NewsService newsService, QuoteService quoteService, CalendarService calendarService, Clock clock, PriceHistoryService priceHistoryService) {
        this.newsService = newsService;
        this.quoteService = quoteService;
        this.calendarService = calendarService;
        this.clock = clock;
        this.priceHistoryService = priceHistoryService;
    }

    @GetMapping("/news")
    public String news(Model model) {
        model.addAttribute("portfolioDays", newsService.getPortfolioNewsByDay());
        model.addAttribute("marketDays", newsService.getMarketNewsByDay());
        model.addAttribute("newsCount", newsService.getNewsCountBySymbol());
        model.addAttribute("tickers", newsService.getTickerBySymbol());
        model.addAttribute("today", LocalDate.now(clock));
        model.addAttribute("starredDays", newsService.getStarredNewsByDay());
        model.addAttribute("starredIds", newsService.getStarredIds());
        model.addAttribute("moves", quoteService.getMoves());
        model.addAttribute("lastSession", quoteService.getLastSession());
        model.addAttribute("comingUp", calendarService.getComingUp());
        model.addAttribute("comingUpDividends", calendarService.getComingUpDividends());
        model.addAttribute("metals", quoteService.getMetals());
        model.addAttribute("metalDays", newsService.getMetalNewsByDay());
        model.addAttribute("priceSeries", priceHistoryService.getRecentSeries());
        return "news";
    }

    @PostMapping("/news/{id}/starred")
    public String changeStarred(@PathVariable long id, @RequestParam boolean starred) {
        newsService.changeStarred(id, starred);
        return "redirect:/news";
    }

}

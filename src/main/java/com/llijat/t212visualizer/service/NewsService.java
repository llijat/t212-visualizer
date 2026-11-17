package com.llijat.t212visualizer.service;

import com.llijat.t212visualizer.dto.NewsDay;
import com.llijat.t212visualizer.model.Position;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.llijat.t212visualizer.dto.FinnhubNewsDto;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class NewsService {
    private static final Logger log = LoggerFactory.getLogger(NewsService.class);

    private final FinnhubClient finnhubClient;
    private List<FinnhubNewsDto> generalNews = List.of();
    private List<FinnhubNewsDto> portfolioNews = List.of();
    private Map<String, String> tickerBySymbol = Map.of();
    private final PositionService positionService;
    private final CompanyProfileService companyProfileService;

    public NewsService(FinnhubClient finnhubClient, PositionService positionService, CompanyProfileService companyProfileService) {
        this.finnhubClient = finnhubClient;
        this.positionService = positionService;
        this.companyProfileService = companyProfileService;
    }

    public List<FinnhubNewsDto> getNews() {
        return generalNews.stream().limit(20).toList();
    }

    public List<FinnhubNewsDto> getPortfolioNews() {
        return portfolioNews;
    }

    public List<String> getPortfolioTickers() {
        return portfolioNews.stream()
                .map(FinnhubNewsDto::related)
                .filter(ticker -> ticker != null && !ticker.isBlank())
                .distinct()
                .sorted()
                .toList();
    }

    public List<NewsDay> getPortfolioNewsByDay() {
        return NewsGrouper.byDay(getPortfolioNews());
    }

    public List<NewsDay> getMarketNewsByDay() {
        return NewsGrouper.byDay(generalNews);
    }

    public Map<String, Long> getNewsCountBySymbol() {
        return portfolioNews.stream()
                .filter(news -> news.related() != null && !news.related().isBlank())
                .collect(Collectors.groupingBy(FinnhubNewsDto::related, TreeMap::new, Collectors.counting()));
    }

    public Map<String, String> getTickerBySymbol() {return tickerBySymbol;}

    @Scheduled(fixedRate = 15*60*1000, initialDelay = 0)
    public void refreshFromFinnhub() {
        log.info("Refreshing News...");

        try {
            generalNews = finnhubClient.fetchGeneralNews();
            log.info("Refreshed {} news.", generalNews.size());
        }
        catch (Exception e) {
            log.warn("Could not refresh the news: {}", e.getMessage());

        }

    }

    @Scheduled(fixedRate = 15*60*1000, initialDelay = 15*1000)
    public void refreshPortfolioNews() {
        LocalDate to = LocalDate.now();
        LocalDate from = to.minusDays(7);

        List<FinnhubNewsDto> collected = new ArrayList<>();
        Set<Long> seenIds = new HashSet<>();
        Map<String, String> tickers = new HashMap<>();

        Map<String, String> symbols = companyProfileService.currentSymbols();
        for (Position position : positionService.getAllPositions()) {
            if (position.getTicker().contains("_US_")) {
                try {
                    String symbol = symbols.getOrDefault(position.getTicker(), position.getSymbol());
                    tickers.put(symbol, position.getTicker());
                    for (FinnhubNewsDto news : finnhubClient.fetchCompanyNews(symbol, from, to)) {
                        if (seenIds.add(news.id())) collected.add(news);
                    }
                } catch (Exception e) {
                    log.warn("Could not load news for {}: {}", position.getTicker(), e.getMessage());
                }
            }
        }
        collected.sort(Comparator.comparingLong(FinnhubNewsDto::datetime).reversed());
        portfolioNews = collected;
        tickerBySymbol = tickers;
        log.info("Refreshed {} portfolio news.", portfolioNews.size());
    }
}



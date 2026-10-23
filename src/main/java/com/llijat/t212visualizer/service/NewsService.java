package com.llijat.t212visualizer.service;

import com.llijat.t212visualizer.model.Position;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.llijat.t212visualizer.dto.FinnhubNewsDto;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.*;

@Service
public class NewsService {
    private static final Logger log = LoggerFactory.getLogger(NewsService.class);

    private final FinnhubClient finnhubClient;
    private List<FinnhubNewsDto> generalNews = List.of();
    private List<FinnhubNewsDto> portfolioNews = List.of();
    private final PositionService positionService;

    public NewsService(FinnhubClient finnhubClient, PositionService positionService) {
        this.finnhubClient = finnhubClient;
        this.positionService = positionService;
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

    @Scheduled(fixedRate = 15*60*1000, initialDelay = 0)
    public void refreshFromFinnhub() {
        log.info("Refreshing News...");

        try {
            generalNews = finnhubClient.fetchGeneralNews();
            log.info("Refreshed {} news.", generalNews.size());
        }
        catch (Exception e) {
            log.warn("No se pudieron refrescar las noticias: {}", e.getMessage());

        }

    }

    @Scheduled(fixedRate = 15*60*1000, initialDelay = 15*1000)
    public void refreshPortfolioNews() {
        LocalDate to = LocalDate.now();
        LocalDate from = to.minusDays(1);

        List<FinnhubNewsDto> collected = new ArrayList<>();
        Set<Long> seenIds = new HashSet<>();

        for (Position position : positionService.getAllPositions()) {
            if (position.getTicker().contains("_US_")) {
                try {
                    for (FinnhubNewsDto news : finnhubClient.fetchCompanyNews(position.getSymbol(), from, to)) {
                        if (seenIds.add(news.id())) collected.add(news);
                    }
                } catch (Exception e) {
                    log.warn("No se pudieron cargar noticias del {}: {}", position.getSymbol(), e.getMessage());
                }
            }
        }
        collected.sort(Comparator.comparingLong(FinnhubNewsDto::datetime).reversed());
        portfolioNews = collected;
        log.info("Refreshed {} portfolio news.", portfolioNews.size());
    }
}



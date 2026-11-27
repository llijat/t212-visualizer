package com.llijat.t212visualizer.service;

import com.llijat.t212visualizer.dto.NewsDay;
import com.llijat.t212visualizer.model.NewsFeed;
import com.llijat.t212visualizer.model.NewsItem;
import com.llijat.t212visualizer.model.Position;
import com.llijat.t212visualizer.repository.NewsItemRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.llijat.t212visualizer.dto.FinnhubNewsDto;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
public class NewsService {
    private static final Logger log = LoggerFactory.getLogger(NewsService.class);
    private static final int PER_HOLDING_PER_DAY = 7;
    private static final Pattern METALS = Pattern.compile("\\b(gold|silver|bullion|precious metals?)\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern OIL = Pattern.compile("\\b(oil|crude|brent|wti|opec)\\b", Pattern.CASE_INSENSITIVE);

    private final FinnhubClient finnhubClient;
    private Map<String, String> tickerBySymbol = Map.of();
    private final PositionService positionService;
    private final CompanyProfileService companyProfileService;
    private final NewsItemRepository newsItemRepository;
    private final Clock clock;

    public NewsService(FinnhubClient finnhubClient, PositionService positionService, CompanyProfileService companyProfileService, NewsItemRepository newsItemRepository, Clock clock) {
        this.finnhubClient = finnhubClient;
        this.positionService = positionService;
        this.companyProfileService = companyProfileService;
        this.newsItemRepository = newsItemRepository;
        this.clock = clock;
    }

    public List<FinnhubNewsDto> getPortfolioNews() {
        Map<String, String> names = nameBySymbol();
        return lastWeek(NewsFeed.PORTFOLIO).stream()
                .filter(news -> NewsRelevance.isAbout(news, names.get(news.related())))
                .toList();
    }

    public List<NewsDay> getLooselyRelatedNewsByDay() {
        Map<String, String> names = nameBySymbol();
        List<FinnhubNewsDto> loose = lastWeek(NewsFeed.PORTFOLIO).stream()
                .filter(news -> !NewsRelevance.isAbout(news, names.get(news.related())))
                .toList();
        return NewsGrouper.byDay(loose, clock.getZone());
    }

    // Symbol (the one the news comes under) → the company name T212 gives
    private Map<String, String> nameBySymbol() {
        Map<String, String> symbols = companyProfileService.currentSymbols();
        Map<String, String> names = new HashMap<>();
        for (Position position : positionService.getAllPositions()) {
            names.put(symbols.getOrDefault(position.getTicker(), position.getSymbol()), position.getName());
        }
        return names;
    }

    public List<NewsDay> getPortfolioNewsByDay() {
        return NewsGrouper.byDay(shownPortfolioNews(), clock.getZone());
    }

    public List<NewsDay> getMarketNewsByDay() {
        return NewsGrouper.byDay(lastWeek(NewsFeed.MARKET), clock.getZone());
    }

    public List<NewsDay> getMetalNewsByDay() {
        List<FinnhubNewsDto> metalNews = lastWeek(NewsFeed.MARKET).stream()
                .filter(news -> METALS.matcher(news.headline()).find())
                .toList();
        return NewsGrouper.byDay(metalNews, clock.getZone());
    }

    public List<NewsDay> getCryptoNewsByDay() {
        return NewsGrouper.byDay(lastWeek(NewsFeed.CRYPTO), clock.getZone());
    }

    public List<NewsDay> getOilNewsByDay() {
        List<FinnhubNewsDto> oilNews = lastWeek(NewsFeed.MARKET).stream()
                .filter(news -> OIL.matcher(news.headline()).find())
                .toList();
        return NewsGrouper.byDay(oilNews, clock.getZone());
    }

    public Map<String, Long> getNewsCountBySymbol() {
        return shownPortfolioNews().stream()
                .filter(news -> news.related() != null && !news.related().isBlank())
                .collect(Collectors.groupingBy(FinnhubNewsDto::related, TreeMap::new, Collectors.counting()));
    }

    public Map<String, String> getTickerBySymbol() {return tickerBySymbol;}

    private void store(List<FinnhubNewsDto> fetched, NewsFeed feed) {
        List<Long> ids = fetched.stream().map(FinnhubNewsDto::id).toList();
        Set<Long> known = newsItemRepository.findAllById(ids).stream()
                .map(NewsItem::getId)
                .collect(Collectors.toSet());

        List<NewsItem> fresh = fetched.stream()
                .filter(news -> !known.contains(news.id()))
                .map(news -> new NewsItem(news, feed))
                .toList();

        newsItemRepository.saveAll(fresh);
        log.info("Stored {} new {} news", fresh.size(), feed);
    }

    private List<FinnhubNewsDto> lastWeek(NewsFeed feed) {
        Instant weekAgo = Instant.now().minus(7, ChronoUnit.DAYS);
        return newsItemRepository.findByFeedAndPublishedAtAfterOrderByPublishedAtDesc(feed, weekAgo).stream()
                .map(NewsItem::toDto)
                .toList();
    }

    private List<FinnhubNewsDto> shownPortfolioNews() {
        Map<String, Integer> shownByHoldingAndDay = new HashMap<>();
        List<FinnhubNewsDto> shown = new ArrayList<>();
        for (FinnhubNewsDto news : getPortfolioNews()) {
            String key = news.related() + " " + news.publishedAt(clock.getZone()).toLocalDate();
            int count = shownByHoldingAndDay.merge(key, 1, Integer::sum);
            if (count <= PER_HOLDING_PER_DAY) shown.add(news);
        }
        return shown;
    }

    private List<FinnhubNewsDto> starred() {
        return newsItemRepository.findByStarredTrueOrderByPublishedAtDesc().stream()
                .map(NewsItem::toDto)
                .toList();
    }

    public List<NewsDay> getStarredNewsByDay() {
        return NewsGrouper.byDay(starred(), clock.getZone());
    }

    public Set<Long> getStarredIds() {
        return starred().stream()
                .map(FinnhubNewsDto::id)
                .collect(Collectors.toSet());
    }

    public void changeStarred(long id, boolean starred) {
        Optional<NewsItem> item = newsItemRepository.findById(id);
        if (item.isEmpty()) return;
        item.get().changeStarred(starred);
        newsItemRepository.save(item.get());
    }

    @Scheduled(fixedRate = 15*60*1000, initialDelay = 0)
    public void refreshFromFinnhub() {
        log.info("Refreshing News...");

        try {
            List<FinnhubNewsDto> fetched = finnhubClient.fetchGeneralNews();
            store(fetched, NewsFeed.MARKET);
            log.info("Refreshed {} news.", fetched.size());
        }
        catch (Exception e) {
            log.warn("Could not refresh the news: {}", e.getMessage());

        }

        try {
            List<FinnhubNewsDto> fetched = finnhubClient.fetchCryptoNews();
            store(fetched, NewsFeed.CRYPTO);
            log.info("Refreshed {} crypto news.", fetched.size());
        }
        catch (Exception e) {
            log.warn("Could not refresh the crypto news: {}", e.getMessage());
        }

    }

    @Scheduled(fixedRate = 15*60*1000, initialDelay = 15*1000)
    public void refreshPortfolioNews() {
        LocalDate to = LocalDate.now(clock);
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
        tickerBySymbol = tickers;
        store(collected, NewsFeed.PORTFOLIO);
        log.info("Refreshed {} portfolio news.", collected.size());
    }

    @Scheduled(fixedRate = 24*60*60*1000, initialDelay = 60*1000)
    public void cleanUpOldNews() {
        Instant monthAgo = Instant.now().minus(30, ChronoUnit.DAYS);
        try {
            long deleted = newsItemRepository.deleteByStarredFalseAndPublishedAtBefore(monthAgo);
            log.info("Deleted {} old news without a star.", deleted);
        } catch (Exception e) {
            log.warn("Could not clean up the old news: {}", e.getMessage());
        }
    }
}



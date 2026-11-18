package com.llijat.t212visualizer.service;

import com.llijat.t212visualizer.dto.FinnhubNewsDto;
import com.llijat.t212visualizer.dto.NewsDay;
import com.llijat.t212visualizer.model.NewsFeed;
import com.llijat.t212visualizer.model.NewsItem;
import com.llijat.t212visualizer.model.Position;
import com.llijat.t212visualizer.repository.NewsItemRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NewsServiceTest {

    @Mock
    private FinnhubClient finnhubClient;

    @Mock
    private PositionService positionService;

    @Mock
    private CompanyProfileService companyProfileService;

    @Mock
    private NewsItemRepository newsItemRepository;

    // Catches the list that the service hands to saveAll, so the test can look inside it
    @Captor
    private ArgumentCaptor<List<NewsItem>> stored;

    private NewsService newsService;

    @BeforeEach
    void setUp() {
        newsService = new NewsService(finnhubClient, positionService, companyProfileService, newsItemRepository);
    }

    private static FinnhubNewsDto news(long id, String related, String utc) {
        return new FinnhubNewsDto("company", Instant.parse(utc).getEpochSecond(), "Headline " + id,
                "https://example.com/" + id, id, related, "", "Summary " + id, "Sample Wire");
    }

    // The table holds these news for that feed (whatever the dates: the repository does the filtering)
    private void theTableHolds(NewsFeed feed, FinnhubNewsDto... news) {
        List<NewsItem> items = Arrays.stream(news).map(item -> new NewsItem(item, feed)).toList();
        when(newsItemRepository.findByFeedAndPublishedAtAfterOrderByPublishedAtDesc(eq(feed), any())).thenReturn(items);
    }

    private static Position position(String ticker, String symbol) {
        Position position = mock(Position.class);
        when(position.getTicker()).thenReturn(ticker);
        // Only US positions ask for their symbol: the others are skipped before
        if (ticker.contains("_US_")) when(position.getSymbol()).thenReturn(symbol);
        return position;
    }

    // ===== Counting the news of each position =====

    @Test
    void eachSymbol_countsItsNews_inAlphabeticalOrder() {
        theTableHolds(NewsFeed.PORTFOLIO,
                news(1, "MSFT", "2026-11-16T10:00:00Z"), news(4, "AAPL", "2026-11-16T11:00:00Z"),
                news(2, "MSFT", "2026-11-15T10:00:00Z"), news(3, "MSFT", "2026-11-14T10:00:00Z"));

        assertEquals(List.of("AAPL", "MSFT"), List.copyOf(newsService.getNewsCountBySymbol().keySet()));
        assertEquals(1L, newsService.getNewsCountBySymbol().get("AAPL"));
        assertEquals(3L, newsService.getNewsCountBySymbol().get("MSFT"));
    }

    @Test
    void newsWithoutASymbol_areNotCounted_andDoNotBreakTheCount() {
        theTableHolds(NewsFeed.PORTFOLIO,
                news(1, "MSFT", "2026-11-16T10:00:00Z"), news(2, null, "2026-11-16T09:00:00Z"), news(3, " ", "2026-11-16T08:00:00Z"));

        assertEquals(Map.of("MSFT", 1L), newsService.getNewsCountBySymbol());
    }

    @Test
    void withAnEmptyTable_thereIsNothingToShow() {
        assertTrue(newsService.getNewsCountBySymbol().isEmpty());
        assertTrue(newsService.getTickerBySymbol().isEmpty());
        assertTrue(newsService.getPortfolioNewsByDay().isEmpty());
        assertTrue(newsService.getMarketNewsByDay().isEmpty());
    }

    // ===== The T212 ticker of each symbol, for the logos =====

    @Test
    void eachSymbol_remembersItsT212Ticker_evenWhenTheSymbolChanged() {
        // T212 still says FB; OpenFIGI found that it is META now
        // Built before stubbing: Mockito cannot set up one mock while it is setting up another
        List<Position> positions = List.of(position("FB_US_EQ", "FB"), position("AAPL_US_EQ", "AAPL"));
        when(positionService.getAllPositions()).thenReturn(positions);
        when(companyProfileService.currentSymbols()).thenReturn(Map.of("FB_US_EQ", "META"));
        when(finnhubClient.fetchCompanyNews(any(), any(), any())).thenReturn(List.of());

        newsService.refreshPortfolioNews();

        assertEquals(Map.of("META", "FB_US_EQ", "AAPL", "AAPL_US_EQ"), newsService.getTickerBySymbol());
    }

    @Test
    void positionsOutsideTheUs_haveNoTicker_becauseTheyHaveNoNews() {
        // Built before stubbing: Mockito cannot set up one mock while it is setting up another
        List<Position> positions = List.of(position("SGLNl_EQ", "SGLN"), position("AAPL_US_EQ", "AAPL"));
        when(positionService.getAllPositions()).thenReturn(positions);
        when(companyProfileService.currentSymbols()).thenReturn(Map.of());
        when(finnhubClient.fetchCompanyNews(eq("AAPL"), any(), any())).thenReturn(List.of());

        newsService.refreshPortfolioNews();

        assertEquals(Map.of("AAPL", "AAPL_US_EQ"), newsService.getTickerBySymbol());
    }

    // ===== Read from the table, grouped by day =====

    @Test
    void thePortfolioNews_comeFromTheTable_groupedByDay() {
        theTableHolds(NewsFeed.PORTFOLIO, news(1, "MSFT", "2026-11-16T10:00:00Z"), news(2, "MSFT", "2026-11-14T10:00:00Z"));

        List<NewsDay> days = newsService.getPortfolioNewsByDay();

        assertEquals(List.of(LocalDate.of(2026, 11, 16), LocalDate.of(2026, 11, 14)), days.stream().map(NewsDay::date).toList());
    }

    @Test
    void theMarketNews_comeFromTheTable_groupedByDay() {
        theTableHolds(NewsFeed.MARKET, news(1, "", "2026-11-15T10:00:00Z"), news(2, "", "2026-11-16T10:00:00Z"));

        List<NewsDay> days = newsService.getMarketNewsByDay();

        assertEquals(LocalDate.of(2026, 11, 16), days.get(0).date());
        assertEquals(2, days.size());
    }

    @Test
    void thePages_askTheTableForTheLastSevenDays() {
        Instant before = Instant.now().minus(7, ChronoUnit.DAYS);
        newsService.getPortfolioNewsByDay();
        Instant after = Instant.now().minus(7, ChronoUnit.DAYS);

        ArgumentCaptor<Instant> since = ArgumentCaptor.forClass(Instant.class);
        verify(newsItemRepository).findByFeedAndPublishedAtAfterOrderByPublishedAtDesc(eq(NewsFeed.PORTFOLIO), since.capture());
        assertFalse(since.getValue().isBefore(before));
        assertFalse(since.getValue().isAfter(after));
    }

    @Test
    void ifFinnhubIsDown_theStoredNewsAreStillShown() {
        when(finnhubClient.fetchGeneralNews()).thenThrow(new RuntimeException("Finnhub is down"));
        theTableHolds(NewsFeed.MARKET, news(1, "", "2026-11-16T10:00:00Z"));

        newsService.refreshFromFinnhub();

        assertEquals(1, newsService.getMarketNewsByDay().size());
    }

    // ===== Storing only the news that are new =====

    @Test
    void theMarketNews_thatAreAlreadyInTheTable_areNotStoredAgain() {
        // News 1 was stored in an earlier refresh; 2 and 3 are new
        when(finnhubClient.fetchGeneralNews()).thenReturn(List.of(
                news(1, "", "2026-11-16T10:00:00Z"), news(2, "", "2026-11-16T11:00:00Z"), news(3, "", "2026-11-16T12:00:00Z")));
        when(newsItemRepository.findAllById(List.of(1L, 2L, 3L))).thenReturn(List.of(
                new NewsItem(news(1, "", "2026-11-16T10:00:00Z"), NewsFeed.MARKET)));

        newsService.refreshFromFinnhub();

        verify(newsItemRepository).saveAll(stored.capture());
        assertEquals(List.of(2L, 3L), stored.getValue().stream().map(NewsItem::getId).toList());
        assertTrue(stored.getValue().stream().allMatch(item -> item.getFeed() == NewsFeed.MARKET));
    }

    @Test
    void whenEverythingIsAlreadyStored_nothingNewIsSaved() {
        when(finnhubClient.fetchGeneralNews()).thenReturn(List.of(news(1, "", "2026-11-16T10:00:00Z")));
        when(newsItemRepository.findAllById(List.of(1L))).thenReturn(List.of(
                new NewsItem(news(1, "", "2026-11-16T10:00:00Z"), NewsFeed.MARKET)));

        newsService.refreshFromFinnhub();

        verify(newsItemRepository).saveAll(stored.capture());
        assertTrue(stored.getValue().isEmpty());
    }

    @Test
    void thePortfolioNews_areStoredAsPortfolio_onlyOnceEachEvenIfTwoPositionsShareThem() {
        // The same article (id 1) comes for MSFT and for AAPL: it is stored once, for the first one
        List<Position> positions = List.of(position("MSFT_US_EQ", "MSFT"), position("AAPL_US_EQ", "AAPL"));
        when(positionService.getAllPositions()).thenReturn(positions);
        when(companyProfileService.currentSymbols()).thenReturn(Map.of());
        when(finnhubClient.fetchCompanyNews(eq("MSFT"), any(), any())).thenReturn(List.of(
                news(1, "MSFT", "2026-11-16T10:00:00Z")));
        when(finnhubClient.fetchCompanyNews(eq("AAPL"), any(), any())).thenReturn(List.of(
                news(1, "AAPL", "2026-11-16T10:00:00Z"), news(2, "AAPL", "2026-11-16T09:00:00Z")));

        newsService.refreshPortfolioNews();

        verify(newsItemRepository).saveAll(stored.capture());
        assertEquals(List.of(1L, 2L), stored.getValue().stream().map(NewsItem::getId).toList());
        assertTrue(stored.getValue().stream().allMatch(item -> item.getFeed() == NewsFeed.PORTFOLIO));
    }

    @Test
    void aStarredNews_isNeverRewrittenByTheRefresh_soItKeepsItsStar() {
        // If the refresh saved it again, a new NewsItem (born without a star) would overwrite the stored one
        NewsItem starred = new NewsItem(news(1, "", "2026-11-16T10:00:00Z"), NewsFeed.MARKET);
        starred.changeStarred(true);
        when(finnhubClient.fetchGeneralNews()).thenReturn(List.of(news(1, "", "2026-11-16T10:00:00Z")));
        when(newsItemRepository.findAllById(List.of(1L))).thenReturn(List.of(starred));

        newsService.refreshFromFinnhub();

        verify(newsItemRepository).saveAll(stored.capture());
        assertTrue(stored.getValue().isEmpty());
    }

    // ===== The star =====

    @Test
    void starringANews_storesTheStar() {
        NewsItem item = new NewsItem(news(1, "MSFT", "2026-11-16T10:00:00Z"), NewsFeed.PORTFOLIO);
        when(newsItemRepository.findById(1L)).thenReturn(Optional.of(item));

        newsService.changeStarred(1L, true);

        assertTrue(item.isStarred());
        verify(newsItemRepository).save(item);
    }

    @Test
    void takingTheStarAway_storesThatToo() {
        NewsItem item = new NewsItem(news(1, "MSFT", "2026-11-16T10:00:00Z"), NewsFeed.PORTFOLIO);
        item.changeStarred(true);
        when(newsItemRepository.findById(1L)).thenReturn(Optional.of(item));

        newsService.changeStarred(1L, false);

        assertFalse(item.isStarred());
        verify(newsItemRepository).save(item);
    }

    @Test
    void starringANewsThatIsGone_doesNothing() {
        // For example, the clean-up deleted it between loading the page and the click
        when(newsItemRepository.findById(1L)).thenReturn(Optional.empty());

        newsService.changeStarred(1L, true);

        verify(newsItemRepository, never()).save(any());
    }

    @Test
    void theStarredNews_comeGroupedByDay_andTheirIdsAreKnown() {
        NewsItem older = new NewsItem(news(1, "MSFT", "2026-09-01T10:00:00Z"), NewsFeed.PORTFOLIO);
        NewsItem newer = new NewsItem(news(2, "", "2026-11-16T10:00:00Z"), NewsFeed.MARKET);
        when(newsItemRepository.findByStarredTrueOrderByPublishedAtDesc()).thenReturn(List.of(newer, older));

        List<NewsDay> days = newsService.getStarredNewsByDay();

        // Months old, and still there: starred news do not expire
        assertEquals(List.of(LocalDate.of(2026, 11, 16), LocalDate.of(2026, 9, 1)), days.stream().map(NewsDay::date).toList());
        assertEquals(Set.of(1L, 2L), newsService.getStarredIds());
    }

    // ===== The daily clean-up =====

    @Test
    void theCleanUp_deletesTheNewsWithoutAStar_olderThanThirtyDays() {
        Instant before = Instant.now().minus(30, ChronoUnit.DAYS);
        newsService.cleanUpOldNews();
        Instant after = Instant.now().minus(30, ChronoUnit.DAYS);

        ArgumentCaptor<Instant> limit = ArgumentCaptor.forClass(Instant.class);
        verify(newsItemRepository).deleteByStarredFalseAndPublishedAtBefore(limit.capture());
        assertFalse(limit.getValue().isBefore(before));
        assertFalse(limit.getValue().isAfter(after));
    }

    @Test
    void ifTheCleanUpFails_theAppCarriesOn() {
        when(newsItemRepository.deleteByStarredFalseAndPublishedAtBefore(any())).thenThrow(new RuntimeException("Database is down"));

        assertDoesNotThrow(() -> newsService.cleanUpOldNews());
    }

    // ===== At most 7 news per holding and day =====

    // n news of that holding on that day, one hour apart, the newest first (as the table gives them)
    private static List<FinnhubNewsDto> many(String related, String day, int n, long firstId) {
        return IntStream.range(0, n)
                .mapToObj(i -> news(firstId + i, related, day + "T" + String.format("%02d", 20 - i) + ":00:00Z"))
                .toList();
    }

    private void theTableHoldsPortfolio(List<FinnhubNewsDto> news) {
        theTableHolds(NewsFeed.PORTFOLIO, news.toArray(FinnhubNewsDto[]::new));
    }

    @Test
    void aBusyHolding_showsOnlyItsSevenNewestNewsOfTheDay() {
        // On its results day, NVIDIA had 210 news: only the 7 newest are shown
        theTableHoldsPortfolio(many("NVDA", "2026-11-17", 10, 1));

        List<NewsDay> days = newsService.getPortfolioNewsByDay();

        assertEquals(List.of(1L, 2L, 3L, 4L, 5L, 6L, 7L), days.get(0).news().stream().map(FinnhubNewsDto::id).toList());
    }

    @Test
    void theOtherHoldings_keepTheirNews_onABusyDay() {
        List<FinnhubNewsDto> news = new ArrayList<>(many("NVDA", "2026-11-17", 10, 1));
        news.addAll(many("AAPL", "2026-11-17", 2, 100));
        theTableHoldsPortfolio(news);

        assertEquals(Map.of("AAPL", 2L, "NVDA", 7L), newsService.getNewsCountBySymbol());
    }

    @Test
    void eachDay_hasItsOwnSeven() {
        List<FinnhubNewsDto> news = new ArrayList<>(many("NVDA", "2026-11-17", 9, 1));
        news.addAll(many("NVDA", "2026-11-16", 9, 100));
        theTableHoldsPortfolio(news);

        List<NewsDay> days = newsService.getPortfolioNewsByDay();

        assertEquals(2, days.size());
        assertEquals(7, days.get(0).news().size());
        assertEquals(7, days.get(1).news().size());
    }

    @Test
    void theCounts_matchWhatIsShown() {
        // If the count said 10 and the page showed 7, choosing that holding would look broken
        theTableHoldsPortfolio(many("NVDA", "2026-11-17", 10, 1));

        int shown = newsService.getPortfolioNewsByDay().stream().mapToInt(day -> day.news().size()).sum();

        assertEquals(7L, newsService.getNewsCountBySymbol().get("NVDA"));
        assertEquals(7, shown);
    }

    @Test
    void theDashboard_stillGetsEveryNews_newestFirst() {
        // The limit is for the news page; the dashboard shows the 5 newest, whoever they are from
        theTableHoldsPortfolio(many("NVDA", "2026-11-17", 10, 1));

        assertEquals(10, newsService.getPortfolioNews().size());
    }
}

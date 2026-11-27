package com.llijat.t212visualizer.controller;

import java.time.Clock;
import java.time.Instant;

import com.llijat.t212visualizer.dto.HoldingMove;
import com.llijat.t212visualizer.dto.CommodityMove;
import com.llijat.t212visualizer.dto.NewsDay;
import com.llijat.t212visualizer.dto.PriceSeries;
import com.llijat.t212visualizer.dto.UpcomingDividend;
import com.llijat.t212visualizer.dto.UpcomingEarnings;
import com.llijat.t212visualizer.service.CalendarService;
import com.llijat.t212visualizer.service.NewsService;
import com.llijat.t212visualizer.service.PriceHistoryService;
import com.llijat.t212visualizer.service.QuoteService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.servlet.view.InternalResourceViewResolver;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

/** The news page: everything it shows comes from NewsService, already grouped and counted. */
@ExtendWith(MockitoExtension.class)
class NewsControllerTest {

    // "Now" for the code under test: fixed, so a test run at midnight does not see two different days
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-11-15T11:00:00Z"), ZoneId.of("Europe/Madrid"));

    @Mock
    private NewsService newsService;

    @Mock
    private QuoteService quoteService;

    @Mock
    private CalendarService calendarService;

    @Mock
    private PriceHistoryService priceHistoryService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        // Without Thymeleaf, the view "news" would be looked for at the URL /news, which is this same page.
        // This resolver turns it into a file name instead, as Thymeleaf does in the real app.
        mockMvc = MockMvcBuilders.standaloneSetup(new NewsController(newsService, quoteService, calendarService, CLOCK, priceHistoryService))
                .setViewResolvers(new InternalResourceViewResolver("/templates/", ".html"))
                .build();
    }

    @Test
    void theNewsPage_getsEverythingItShows() throws Exception {
        List<NewsDay> portfolio = List.of(new NewsDay(LocalDate.of(2026, 11, 16), List.of()));
        List<NewsDay> market = List.of(new NewsDay(LocalDate.of(2026, 11, 15), List.of()));
        when(newsService.getPortfolioNewsByDay()).thenReturn(portfolio);
        when(newsService.getMarketNewsByDay()).thenReturn(market);
        when(newsService.getNewsCountBySymbol()).thenReturn(Map.of("AAPL", 3L));
        when(newsService.getTickerBySymbol()).thenReturn(Map.of("AAPL", "AAPL_US_EQ"));

        mockMvc.perform(get("/news"))
                .andExpect(status().isOk())
                .andExpect(view().name("news"))
                .andExpect(model().attribute("portfolioDays", portfolio))
                .andExpect(model().attribute("marketDays", market))
                .andExpect(model().attribute("newsCount", Map.of("AAPL", 3L)))
                .andExpect(model().attribute("tickers", Map.of("AAPL", "AAPL_US_EQ")));
    }

    @Test
    void theHoldings_getTheirPriceSeries_forTheLinesNextToTheirNames() throws Exception {
        Map<String, PriceSeries> series = Map.of("AAPL_US_EQ",
                new PriceSeries(List.of(new BigDecimal("230"), new BigDecimal("232")), new BigDecimal("0.87")));
        when(priceHistoryService.getRecentSeries()).thenReturn(series);

        mockMvc.perform(get("/news"))
                .andExpect(model().attribute("priceSeries", series));
    }

    @Test
    void today_isTheDayInSpain_soThePageCanSayTodayAndYesterday() throws Exception {
        when(newsService.getPortfolioNewsByDay()).thenReturn(List.of());
        when(newsService.getMarketNewsByDay()).thenReturn(List.of());
        when(newsService.getNewsCountBySymbol()).thenReturn(Map.of());
        when(newsService.getTickerBySymbol()).thenReturn(Map.of());

        mockMvc.perform(get("/news"))
                .andExpect(model().attribute("today", LocalDate.now(CLOCK)));
    }

    @Test
    void thePage_onlyAnswersToGet() throws Exception {
        mockMvc.perform(post("/news")).andExpect(status().isMethodNotAllowed());
    }

    // ===== The star =====

    @Test
    void thePage_getsTheStarredNews_andWhichOnesAreStarred() throws Exception {
        List<NewsDay> starred = List.of(new NewsDay(LocalDate.of(2026, 9, 1), List.of()));
        when(newsService.getStarredNewsByDay()).thenReturn(starred);
        when(newsService.getStarredIds()).thenReturn(Set.of(7L));

        mockMvc.perform(get("/news"))
                .andExpect(model().attribute("starredDays", starred))
                .andExpect(model().attribute("starredIds", Set.of(7L)));
    }

    @Test
    void thePage_getsTheMetals_andTheirNews() throws Exception {
        List<CommodityMove> metals = List.of(new CommodityMove("GLD", new BigDecimal("1.2")));
        List<NewsDay> metalDays = List.of(new NewsDay(LocalDate.of(2026, 11, 20), List.of()));
        when(quoteService.getCommodities()).thenReturn(metals);
        when(newsService.getMetalNewsByDay()).thenReturn(metalDays);

        mockMvc.perform(get("/news"))
                .andExpect(model().attribute("commodities", metals))
                .andExpect(model().attribute("metalDays", metalDays));
    }

    @Test
    void thePage_getsTheOilAndCryptoNews() throws Exception {
        List<NewsDay> oilDays = List.of(new NewsDay(LocalDate.of(2026, 11, 26), List.of()));
        List<NewsDay> cryptoDays = List.of(new NewsDay(LocalDate.of(2026, 11, 25), List.of()));
        when(newsService.getOilNewsByDay()).thenReturn(oilDays);
        when(newsService.getCryptoNewsByDay()).thenReturn(cryptoDays);

        mockMvc.perform(get("/news"))
                .andExpect(model().attribute("oilDays", oilDays))
                .andExpect(model().attribute("cryptoDays", cryptoDays));
    }

    @Test
    void thePage_getsTheStoriesThatOnlyMentionAHoldingInPassing() throws Exception {
        List<NewsDay> looseDays = List.of(new NewsDay(LocalDate.of(2026, 11, 26), List.of()));
        when(newsService.getLooselyRelatedNewsByDay()).thenReturn(looseDays);

        mockMvc.perform(get("/news"))
                .andExpect(model().attribute("looseDays", looseDays));
    }

    @Test
    void thePage_getsTheDividendsComingUp() throws Exception {
        List<UpcomingDividend> dividends = List.of(new UpcomingDividend("MU", "MU_US_EQ", LocalDate.of(2026, 12, 3), null, new BigDecimal("0.86")));
        when(calendarService.getComingUpDividends()).thenReturn(dividends);

        mockMvc.perform(get("/news"))
                .andExpect(model().attribute("comingUpDividends", dividends));
    }

    @Test
    void starringANews_storesIt_andGoesBackToThePage() throws Exception {
        mockMvc.perform(post("/news/12345/starred").param("starred", "true"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/news"));

        verify(newsService).changeStarred(12345L, true);
    }

    @Test
    void takingTheStarAway_isTheSameRequest_withFalse() throws Exception {
        mockMvc.perform(post("/news/12345/starred").param("starred", "false"))
                .andExpect(redirectedUrl("/news"));

        verify(newsService).changeStarred(12345L, false);
    }

    @Test
    void aNewsIdThatIsNotANumber_isRejected_beforeReachingTheService() throws Exception {
        mockMvc.perform(post("/news/abc/starred").param("starred", "true"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(newsService);
    }

    @Test
    void withoutSayingTheStateWanted_nothingChanges() throws Exception {
        mockMvc.perform(post("/news/12345/starred"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(newsService);
    }

    @Test
    void theStar_cannotBeChangedWithALink() throws Exception {
        // A GET must never change anything: browsers preload links, and search engines follow them
        mockMvc.perform(get("/news/12345/starred").param("starred", "true"))
                .andExpect(status().isMethodNotAllowed());

        verifyNoInteractions(newsService);
    }

    // ===== Moving today =====

    @Test
    void thePage_getsTheMoves_andTheDayTheyAreFrom() throws Exception {
        List<HoldingMove> moves = List.of(new HoldingMove("AAPL", "AAPL_US_EQ", new BigDecimal("-4.19"), new BigDecimal("15.0")));
        when(quoteService.getMoves()).thenReturn(moves);
        when(quoteService.getLastSession()).thenReturn(Optional.of(LocalDate.of(2026, 11, 16)));

        mockMvc.perform(get("/news"))
                .andExpect(model().attribute("moves", moves))
                .andExpect(model().attribute("lastSession", Optional.of(LocalDate.of(2026, 11, 16))));
    }

    // ===== Coming up =====

    @Test
    void thePage_getsTheNextResultsOfTheHoldings() throws Exception {
        List<UpcomingEarnings> comingUp = List.of(new UpcomingEarnings("NVDA", "NVDA_US_EQ", LocalDate.of(2026, 11, 19), "amc"));
        when(calendarService.getComingUp()).thenReturn(comingUp);

        mockMvc.perform(get("/news"))
                .andExpect(model().attribute("comingUp", comingUp));
    }
}

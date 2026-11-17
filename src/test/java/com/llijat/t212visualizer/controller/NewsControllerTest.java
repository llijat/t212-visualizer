package com.llijat.t212visualizer.controller;

import com.llijat.t212visualizer.dto.NewsDay;
import com.llijat.t212visualizer.service.NewsService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.servlet.view.InternalResourceViewResolver;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

/** The news page: everything it shows comes from NewsService, already grouped and counted. */
@ExtendWith(MockitoExtension.class)
class NewsControllerTest {

    @Mock
    private NewsService newsService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        // Without Thymeleaf, the view "news" would be looked for at the URL /news, which is this same page.
        // This resolver turns it into a file name instead, as Thymeleaf does in the real app.
        mockMvc = MockMvcBuilders.standaloneSetup(new NewsController(newsService))
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
    void today_isTheDayInSpain_soThePageCanSayTodayAndYesterday() throws Exception {
        when(newsService.getPortfolioNewsByDay()).thenReturn(List.of());
        when(newsService.getMarketNewsByDay()).thenReturn(List.of());
        when(newsService.getNewsCountBySymbol()).thenReturn(Map.of());
        when(newsService.getTickerBySymbol()).thenReturn(Map.of());

        mockMvc.perform(get("/news"))
                .andExpect(model().attribute("today", LocalDate.now(ZoneId.of("Europe/Madrid"))));
    }

    @Test
    void thePage_onlyAnswersToGet() throws Exception {
        mockMvc.perform(post("/news")).andExpect(status().isMethodNotAllowed());
    }
}

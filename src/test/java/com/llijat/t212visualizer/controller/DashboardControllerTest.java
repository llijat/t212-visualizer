package com.llijat.t212visualizer.controller;

import com.llijat.t212visualizer.dto.DailyChange;
import com.llijat.t212visualizer.dto.DividendHistory;
import com.llijat.t212visualizer.dto.HoldingMove;
import com.llijat.t212visualizer.dto.MetalMove;
import com.llijat.t212visualizer.service.AccountService;
import com.llijat.t212visualizer.service.AccountSnapshotService;
import com.llijat.t212visualizer.service.CalendarService;
import com.llijat.t212visualizer.service.CompanyProfileService;
import com.llijat.t212visualizer.service.DividendHistoryService;
import com.llijat.t212visualizer.service.NewsService;
import com.llijat.t212visualizer.service.PositionService;
import com.llijat.t212visualizer.service.QuoteService;
import com.llijat.t212visualizer.service.TransactionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.servlet.view.InternalResourceViewResolver;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

/** The portfolio page. For now it only checks the "Today" card: what moves, and gold and silver next to it. */
@ExtendWith(MockitoExtension.class)
class DashboardControllerTest {

    @Mock private PositionService positionService;
    @Mock private NewsService newsService;
    @Mock private AccountService accountService;
    @Mock private TransactionService transactionService;
    @Mock private CompanyProfileService companyProfileService;
    @Mock private QuoteService quoteService;
    @Mock private CalendarService calendarService;
    @Mock private DividendHistoryService dividendHistoryService;
    @Mock private AccountSnapshotService accountSnapshotService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        // The same trick as in NewsControllerTest: the view name becomes a file name, so "/" is not looked up again
        DashboardController controller = new DashboardController(positionService, newsService, accountService,
                transactionService, companyProfileService, quoteService, calendarService, dividendHistoryService, accountSnapshotService);
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setViewResolvers(new InternalResourceViewResolver("/templates/", ".html"))
                .build();
    }

    @Test
    void thePortfolioPage_isTheDashboardView() throws Exception {
        mockMvc.perform(get("/"))
                .andExpect(status().isOk())
                .andExpect(view().name("dashboard"));
    }

    @Test
    void theTodayCard_getsTheMoves_andTheMetalsNextToThem() throws Exception {
        List<HoldingMove> moves = List.of(new HoldingMove("AAPL", "AAPL_US_EQ", new BigDecimal("-4.19"), new BigDecimal("15.0")));
        List<MetalMove> metals = List.of(new MetalMove("GLD", new BigDecimal("0.85")), new MetalMove("SLV", new BigDecimal("-3.77")));
        when(quoteService.getMoves()).thenReturn(moves);
        when(quoteService.getMetals()).thenReturn(metals);

        mockMvc.perform(get("/"))
                .andExpect(model().attribute("moves", moves))
                .andExpect(model().attribute("metals", metals));
    }

    @Test
    void thePage_getsTheDividendsOfTheLast12Months() throws Exception {
        DividendHistory history = new DividendHistory(List.of());
        when(dividendHistoryService.getLast12Months()).thenReturn(history);

        mockMvc.perform(get("/"))
                .andExpect(model().attribute("dividendHistory", history));
    }

    @Test
    void thePage_getsWhatTheAccountMadeToday() throws Exception {
        DailyChange today = new DailyChange(new BigDecimal("12.30"), new BigDecimal("0.14"));
        when(accountSnapshotService.getTodayChange()).thenReturn(today);

        mockMvc.perform(get("/"))
                .andExpect(model().attribute("todayChange", today));
    }

    @Test
    void thePage_getsTheT212TickerOfEachSymbol_forTheLogosOfTheNews() throws Exception {
        when(newsService.getTickerBySymbol()).thenReturn(Map.of("NVDA", "NVDA_US_EQ"));

        mockMvc.perform(get("/"))
                .andExpect(model().attribute("tickers", Map.of("NVDA", "NVDA_US_EQ")));
    }
}

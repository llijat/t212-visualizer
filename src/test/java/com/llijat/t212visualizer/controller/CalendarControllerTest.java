package com.llijat.t212visualizer.controller;

import com.llijat.t212visualizer.dto.CalendarMonth;
import com.llijat.t212visualizer.dto.UpcomingDividend;
import com.llijat.t212visualizer.dto.UpcomingEarnings;
import com.llijat.t212visualizer.service.CalendarService;
import com.llijat.t212visualizer.service.CompanyProfileService;
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
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.List;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

@ExtendWith(MockitoExtension.class)
class CalendarControllerTest {

    private static final ZoneId SPAIN = ZoneId.of("Europe/Madrid");

    @Mock
    private CalendarService calendarService;

    @Mock
    private CompanyProfileService companyProfileService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        // The same trick as in NewsControllerTest: the view name becomes a file name
        mockMvc = MockMvcBuilders.standaloneSetup(new CalendarController(calendarService, companyProfileService))
                .setViewResolvers(new InternalResourceViewResolver("/templates/", ".html"))
                .build();
    }

    @Test
    void withoutAMonth_itShowsTheCurrentOne_inSpanishTime() throws Exception {
        YearMonth now = YearMonth.now(SPAIN);
        CalendarMonth month = new CalendarMonth(now, List.of());
        when(calendarService.getMonth(now)).thenReturn(month);

        mockMvc.perform(get("/calendar"))
                .andExpect(view().name("calendar"))
                .andExpect(model().attribute("calendar", month));
    }

    @Test
    void aMonthInTheAddress_isTheOneShown() throws Exception {
        mockMvc.perform(get("/calendar").param("month", "2027-01"));

        verify(calendarService).getMonth(YearMonth.of(2027, 1));
    }

    @Test
    void thePanel_getsWhatComesUp_whateverMonthIsShown() throws Exception {
        LocalDate today = LocalDate.now(SPAIN);
        List<UpcomingEarnings> earnings = List.of(new UpcomingEarnings("NVDA", "NVDA_US_EQ", today.plusDays(2), "amc"));
        List<UpcomingDividend> dividends = List.of(new UpcomingDividend("MU", "MU_US_EQ", today.plusDays(5), null, new BigDecimal("0.86")));
        when(calendarService.getComingUp()).thenReturn(earnings);
        when(calendarService.getComingUpDividends()).thenReturn(dividends);

        mockMvc.perform(get("/calendar").param("month", "2027-01"))
                .andExpect(model().attribute("comingUp", earnings))
                .andExpect(model().attribute("comingUpDividends", dividends))
                .andExpect(model().attribute("today", today));
    }
}

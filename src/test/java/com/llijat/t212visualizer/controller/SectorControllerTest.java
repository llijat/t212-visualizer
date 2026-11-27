package com.llijat.t212visualizer.controller;

import com.llijat.t212visualizer.dto.SectorMap;
import com.llijat.t212visualizer.service.SectorOverrideService;
import com.llijat.t212visualizer.service.SectorService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.servlet.view.InternalResourceViewResolver;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

@ExtendWith(MockitoExtension.class)
class SectorControllerTest {

    // 23:30 UTC on Friday the 20th is already 00:30 on Saturday the 21st in Madrid: the date must be Madrid's
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-11-20T23:30:00Z"), ZoneId.of("Europe/Madrid"));

    @Mock
    private SectorService sectorService;

    @Mock
    private SectorOverrideService sectorOverrideService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        // The same trick as in the other controller tests: the view name becomes a file name
        mockMvc = MockMvcBuilders.standaloneSetup(new SectorController(sectorService, CLOCK, sectorOverrideService))
                .setViewResolvers(new InternalResourceViewResolver("/templates/", ".html"))
                .build();
    }

    @Test
    void theSectorsPage_getsTheMap_andTodayInSpanishTime() throws Exception {
        SectorMap map = new SectorMap(List.of(), new BigDecimal("1000"));
        when(sectorService.getSectorMap()).thenReturn(map);

        mockMvc.perform(get("/sectors"))
                .andExpect(status().isOk())
                .andExpect(view().name("sectors"))
                .andExpect(model().attribute("sectorMap", map))
                .andExpect(model().attribute("today", LocalDate.of(2026, 11, 21)));   // already Saturday in Madrid
    }

    // ===== Moving a stock to another subsector =====

    @Test
    void movingAStock_savesIt_andOpensTheSubsectorItWentTo() throws Exception {
        mockMvc.perform(post("/sectors/override").param("ticker", "RWEd_EQ").param("subsector", "Clean energy"))
                .andExpect(redirectedUrl("/sectors?open=Clean+energy"));   // a "+" is a space in a query; the page reads it back as one

        verify(sectorOverrideService).saveSubsector("RWEd_EQ", "Clean energy");
    }

    @Test
    void aRefusedMove_comesBackWithTheReason() throws Exception {
        doThrow(new IllegalArgumentException("Choose a subsector.")).when(sectorOverrideService).saveSubsector("RWEd_EQ", "Nowhere");

        mockMvc.perform(post("/sectors/override").param("ticker", "RWEd_EQ").param("subsector", "Nowhere"))
                .andExpect(redirectedUrl("/sectors"))
                .andExpect(flash().attribute("overrideError", "Choose a subsector."));
    }

    @Test
    void backToAutomatic_forgetsTheChoice() throws Exception {
        mockMvc.perform(post("/sectors/override/reset").param("ticker", "RWEd_EQ"))
                .andExpect(redirectedUrl("/sectors"));

        verify(sectorOverrideService).resetSubsector("RWEd_EQ");
    }
}

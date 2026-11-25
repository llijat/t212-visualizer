package com.llijat.t212visualizer.controller;

import com.llijat.t212visualizer.service.SectorService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import java.time.Clock;
import java.time.LocalDate;

@Controller
public class SectorController {
    private final SectorService sectorService;
    private final Clock clock;

    public SectorController(SectorService sectorService, Clock clock) {
        this.sectorService = sectorService;
        this.clock = clock;
    }

    @GetMapping("/sectors")
    public String sector(Model model) {
        model.addAttribute("sectorMap", sectorService.getSectorMap());
        model.addAttribute("today", LocalDate.now(clock));
        return "sectors";
    }

}

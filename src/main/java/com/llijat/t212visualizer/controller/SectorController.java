package com.llijat.t212visualizer.controller;

import com.llijat.t212visualizer.service.SectorOverrideService;
import com.llijat.t212visualizer.service.SectorService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.Clock;
import java.time.LocalDate;

@Controller
public class SectorController {
    private final SectorService sectorService;
    private final Clock clock;
    private final SectorOverrideService sectorOverrideService;

    public SectorController(SectorService sectorService, Clock clock, SectorOverrideService sectorOverrideService) {
        this.sectorService = sectorService;
        this.clock = clock;
        this.sectorOverrideService = sectorOverrideService;
    }

    @GetMapping("/sectors")
    public String sector(Model model) {
        model.addAttribute("sectorMap", sectorService.getSectorMap());
        model.addAttribute("today", LocalDate.now(clock));
        return "sectors";
    }

    @PostMapping("/sectors/override")
    public String saveOverride(@RequestParam String ticker, @RequestParam String subsector, RedirectAttributes redirect) {
        try {
            sectorOverrideService.saveSubsector(ticker, subsector);
            redirect.addAttribute("open", subsector);   // the page opens the subsector the stock went to
        } catch (IllegalArgumentException e) {
            redirect.addFlashAttribute("overrideError", e.getMessage());
        }
        return "redirect:/sectors";
    }

    @PostMapping("/sectors/override/reset")
    public String resetOverride(@RequestParam String ticker) {
        sectorOverrideService.resetSubsector(ticker);
        return "redirect:/sectors";
    }

}

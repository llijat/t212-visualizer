package com.llijat.t212visualizer.controller;

import com.llijat.t212visualizer.service.CalendarService;
import com.llijat.t212visualizer.service.CompanyProfileService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.time.YearMonth;
import java.time.ZoneId;

@Controller
public class CalendarController {
    private final CalendarService calendarService;
    private static final ZoneId SPAIN = ZoneId.of("Europe/Madrid");
    private final CompanyProfileService companyProfileService;

    public CalendarController(CalendarService calendarService, CompanyProfileService companyProfileService) {
        this.calendarService = calendarService;
        this.companyProfileService = companyProfileService;
    }

    @GetMapping("/calendar")
    public String calendar(@RequestParam(required = false) YearMonth month, Model model) {
        if (month == null) month = YearMonth.now(SPAIN);
        model.addAttribute("calendar",calendarService.getMonth(month));
        model.addAttribute("symbols", companyProfileService.currentSymbols());
        return "calendar";
    }
}

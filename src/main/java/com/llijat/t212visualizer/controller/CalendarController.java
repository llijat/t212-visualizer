package com.llijat.t212visualizer.controller;

import com.llijat.t212visualizer.service.CalendarService;
import com.llijat.t212visualizer.service.CompanyProfileService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;

@Controller
public class CalendarController {
    private final CalendarService calendarService;
    private final Clock clock;
    private final CompanyProfileService companyProfileService;

    public CalendarController(CalendarService calendarService, CompanyProfileService companyProfileService, Clock clock) {
        this.calendarService = calendarService;
        this.companyProfileService = companyProfileService;
        this.clock = clock;
    }

    @GetMapping("/calendar")
    public String calendar(@RequestParam(required = false) YearMonth month, Model model) {
        if (month == null) month = YearMonth.now(clock);
        model.addAttribute("calendar",calendarService.getMonth(month));
        model.addAttribute("symbols", companyProfileService.currentSymbols());
        model.addAttribute("comingUp", calendarService.getComingUp());
        model.addAttribute("comingUpDividends", calendarService.getComingUpDividends());
        model.addAttribute("today", LocalDate.now(clock));
        return "calendar";
    }
}

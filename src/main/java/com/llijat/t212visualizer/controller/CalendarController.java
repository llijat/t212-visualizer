package com.llijat.t212visualizer.controller;

import com.llijat.t212visualizer.service.CalendarService;
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

    public CalendarController(CalendarService calendarService) {
        this.calendarService = calendarService;
    }

    @GetMapping("/calendar")
    public String calendar(@RequestParam(required = false) YearMonth month, Model model) {
        if (month == null) month = YearMonth.now(SPAIN);
        model.addAttribute("calendar",calendarService.getMonth(month));
        return "calendar";
    }
}

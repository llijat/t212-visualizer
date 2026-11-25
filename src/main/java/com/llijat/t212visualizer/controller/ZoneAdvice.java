package com.llijat.t212visualizer.controller;

import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

import java.time.Clock;
import java.time.ZoneId;

/** Every page gets "zone", the app's time zone, so the templates can show times where the app is used (n.publishedAt(zone)). */
@ControllerAdvice
public class ZoneAdvice {
    private final Clock clock;

    public ZoneAdvice(Clock clock) {
        this.clock = clock;
    }

    @ModelAttribute("zone")
    public ZoneId zone() {
        return clock.getZone();
    }
}

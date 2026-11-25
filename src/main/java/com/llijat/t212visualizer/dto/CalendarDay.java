package com.llijat.t212visualizer.dto;

import com.llijat.t212visualizer.model.EarningsEvent;

import java.time.LocalDate;
import java.util.List;

public record CalendarDay (
        LocalDate date,
        boolean inMonth,
        boolean today,
        List<EarningsEvent> earnings,
        List<CalendarDividend> dividends,
        List<CalendarSubscription> subscriptions
) {}

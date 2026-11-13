package com.llijat.t212visualizer.dto;

import java.time.YearMonth;
import java.util.List;

public record CalendarMonth(
        YearMonth month,
        List<CalendarDay> days
) {}

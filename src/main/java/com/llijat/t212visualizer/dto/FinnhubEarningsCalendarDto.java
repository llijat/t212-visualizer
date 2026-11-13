package com.llijat.t212visualizer.dto;

import java.util.List;

public record FinnhubEarningsCalendarDto(
    List<FinnhubEarningsDto> earningsCalendar
) {}
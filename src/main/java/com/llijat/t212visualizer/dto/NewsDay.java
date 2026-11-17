package com.llijat.t212visualizer.dto;

import java.time.LocalDate;
import java.util.List;

public record NewsDay(
        LocalDate date,
        List<FinnhubNewsDto> news
) {}

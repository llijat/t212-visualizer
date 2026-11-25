package com.llijat.t212visualizer.service;

import com.llijat.t212visualizer.dto.AlphaVantageDividendDto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

public interface AlphaVantageClient {
    List<AlphaVantageDividendDto> fetchDividends(String symbol);
    Map<LocalDate, BigDecimal> fetchDailyCloses(String symbol);
}

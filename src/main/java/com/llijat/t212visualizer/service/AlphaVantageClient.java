package com.llijat.t212visualizer.service;

import com.llijat.t212visualizer.dto.AlphaVantageDividendDto;

import java.util.List;

public interface AlphaVantageClient {
    List<AlphaVantageDividendDto> fetchDividends(String symbol);
}

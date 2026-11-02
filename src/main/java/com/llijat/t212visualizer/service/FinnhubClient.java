package com.llijat.t212visualizer.service;

import com.llijat.t212visualizer.dto.FinnhubNewsDto;

import java.time.LocalDate;
import java.util.List;

public interface FinnhubClient {
    List<FinnhubNewsDto> fetchGeneralNews();
    List<FinnhubNewsDto> fetchCompanyNews(String symbol, LocalDate from, LocalDate to);
}

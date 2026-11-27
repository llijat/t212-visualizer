package com.llijat.t212visualizer.service;

import com.llijat.t212visualizer.dto.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface FinnhubClient {
    List<FinnhubNewsDto> fetchGeneralNews();
    List<FinnhubNewsDto> fetchCompanyNews(String symbol, LocalDate from, LocalDate to);
    List<FinnhubEarningsDto> fetchEarnings(String symbol, LocalDate from, LocalDate to);
    FinnhubCompanyProfileDto fetchCompanyProfile(String symbol);
    Optional<LogoImage> downloadLogo(String url);
    FinnhubQuoteDto fetchQuote(String symbol);
    List<FinnhubNewsDto> fetchCryptoNews();
}

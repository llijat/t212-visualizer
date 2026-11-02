package com.llijat.t212visualizer.service;

import com.llijat.t212visualizer.dto.FinnhubNewsDto;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.time.LocalDate;
import java.util.List;

@Service
@Profile("!demo")
public class LiveFinnhubClient implements FinnhubClient {

    private final RestClient finnhubRestClient;

    public LiveFinnhubClient(@Qualifier("finnhubRestClient") RestClient finnhubRestClient) {
        this.finnhubRestClient = finnhubRestClient;
    }

    @Override
    public List<FinnhubNewsDto> fetchGeneralNews() {
        FinnhubNewsDto[] response = finnhubRestClient.get()
                .uri("/news?category=general")
                .retrieve()
                .body(FinnhubNewsDto[].class);

        return response == null ? List.of() : List.of(response);
    }

    @Override
    public List<FinnhubNewsDto> fetchCompanyNews(String symbol, LocalDate from, LocalDate to) {
        FinnhubNewsDto[] response = finnhubRestClient.get()
                .uri("/company-news?symbol={symbol}&from={from}&to={to}", symbol, from, to)
                .retrieve()
                .body(FinnhubNewsDto[].class);

        return response == null ? List.of() : List.of(response);
    }
}

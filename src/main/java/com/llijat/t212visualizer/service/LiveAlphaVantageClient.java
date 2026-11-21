package com.llijat.t212visualizer.service;

import com.llijat.t212visualizer.dto.AlphaVantageDividendDto;
import com.llijat.t212visualizer.dto.AlphaVantageDividendsDto;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.List;

@Service
@Profile("!demo")
public class LiveAlphaVantageClient implements AlphaVantageClient {
    private final RestClient alphaVantageRestClient;

    public LiveAlphaVantageClient(@Qualifier("alphaVantageRestClient") RestClient alphaVantageRestClient) {
        this.alphaVantageRestClient = alphaVantageRestClient;
    }

    @Override
    public List<AlphaVantageDividendDto> fetchDividends(String symbol) {
        AlphaVantageDividendsDto response = alphaVantageRestClient.get()
                .uri("/query?function=DIVIDENDS&symbol={symbol}", symbol)
                .retrieve()
                .body(AlphaVantageDividendsDto.class);
        // Over the limit, Alpha Vantage still answers 200, with a notice instead of "data"
        if (response == null || response.data() == null) {
            throw new IllegalStateException("Alpha Vantage answered without dividends for " + symbol + " (daily limit reached?)");
        }
        return response.data();
    }
}
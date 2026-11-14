package com.llijat.t212visualizer.service;

import com.llijat.t212visualizer.dto.*;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Profile;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.net.URI;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Set;

@Service
@Profile("!demo")
public class LiveFinnhubClient implements FinnhubClient {
    private final RestClient finnhubRestClient;
    private final RestClient finnhubDownloadRestClient;
    private static final Set<String> ALLOWED_TYPES = Set.of("image/png","image/jpeg","image/webp");


    public LiveFinnhubClient(@Qualifier("finnhubRestClient") RestClient finnhubRestClient, @Qualifier("finnhubDownloadRestClient") RestClient finnhubDownloadRestClient) {
        this.finnhubRestClient = finnhubRestClient;
        this.finnhubDownloadRestClient = finnhubDownloadRestClient;
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

    @Override
    public List<FinnhubEarningsDto> fetchEarnings(String symbol, LocalDate from, LocalDate to) {
        FinnhubEarningsCalendarDto response = finnhubRestClient.get()
                .uri("/calendar/earnings?symbol={symbol}&from={from}&to={to}", symbol, from, to)
                .retrieve()
                .body(FinnhubEarningsCalendarDto.class);
        if (response == null || response.earningsCalendar() == null) return List.of();
        else return response.earningsCalendar();
    }

    @Override
    public FinnhubCompanyProfileDto fetchCompanyProfile(String symbol) {
        return finnhubRestClient.get()
                .uri("/stock/profile2?symbol={symbol}", symbol)
                .retrieve()
                .body(FinnhubCompanyProfileDto.class);
    }

    @Override
    public Optional<LogoImage> downloadLogo(String url) {
        ResponseEntity<byte[]> response = finnhubDownloadRestClient.get()
                .uri(URI.create(url))
                .retrieve()
                .toEntity(byte[].class);

        byte[] bytes = response.getBody();
        MediaType type = response.getHeaders().getContentType();

        if (type == null) return Optional.empty();
        String contentType = type.getType() + "/" + type.getSubtype();

        if (bytes == null || bytes.length == 0 || !ALLOWED_TYPES.contains(contentType)) return Optional.empty();
        return Optional.of(new LogoImage(bytes, contentType));
    }

}

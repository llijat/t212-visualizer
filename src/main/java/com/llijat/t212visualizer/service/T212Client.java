package com.llijat.t212visualizer.service;

import com.llijat.t212visualizer.dto.T212AccountSummaryDto;
import com.llijat.t212visualizer.dto.T212PositionDto;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.List;

@Service
public class T212Client {

    private final RestClient t212RestClient;

    public T212Client(@Qualifier("t212RestClient") RestClient t212RestClient) {
        this.t212RestClient = t212RestClient;
    }

    /** GET /equity/positions — all open positions. Rate limit: 1 req/1s (confirmed). */
    public List<T212PositionDto> fetchPositions() {
        T212PositionDto[] response = t212RestClient.get()
                .uri("/equity/positions")
                .retrieve()
                .body(T212PositionDto[].class);

        return response == null ? List.of() : List.of(response);
    }

    public T212AccountSummaryDto fetchAccountSummary() {
        return t212RestClient.get()
                .uri("/equity/account/summary")
                .retrieve()
                .body(T212AccountSummaryDto.class);
    }
}

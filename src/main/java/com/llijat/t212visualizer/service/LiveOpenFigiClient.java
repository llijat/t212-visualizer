package com.llijat.t212visualizer.service;

import com.llijat.t212visualizer.dto.OpenFigiJobDto;
import com.llijat.t212visualizer.dto.OpenFigiListingDto;
import com.llijat.t212visualizer.dto.OpenFigiResultDto;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Profile;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@Profile("!demo")
public class LiveOpenFigiClient implements OpenFigiClient{
    private static final int BATCH_SIZE = 10;
    private final RestClient openFigiRestClient;

    public LiveOpenFigiClient(@Qualifier("openFigiRestClient") RestClient openFigiRestClient) {
        this.openFigiRestClient = openFigiRestClient;
    }

    @Override
    public Map<String, List<OpenFigiListingDto>> findListings(List<String> isins) {
        Map<String, List<OpenFigiListingDto>> listings = new HashMap<>();
        for (int start = 0; start < isins.size(); start += BATCH_SIZE) {
            List<String> batch = isins.subList(start, Math.min(start + BATCH_SIZE, isins.size()));

            List<OpenFigiJobDto> jobs = batch.stream().map(isin -> new OpenFigiJobDto("ID_ISIN", isin)).toList();
            OpenFigiResultDto[] results = openFigiRestClient.post()
                    .uri("/v3/mapping")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(jobs)
                    .retrieve()
                    .body(OpenFigiResultDto[].class);

            if (results == null || results.length != batch.size()) {
                throw new IllegalStateException("OpenFIGI answered " + (results == null ? 0 : results.length)
                        + " results for " + batch.size() + " ISINs");
            }

            for (int i = 0; i < batch.size(); ++i) {
                OpenFigiResultDto result = results[i];
                listings.put(batch.get(i), result.data() == null ? List.of() : result.data());
            }
        }
        return listings;
    }
}

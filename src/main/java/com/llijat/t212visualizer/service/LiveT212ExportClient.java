package com.llijat.t212visualizer.service;

import com.llijat.t212visualizer.dto.T212ExportCreatedDto;
import com.llijat.t212visualizer.dto.T212ExportReportDto;
import com.llijat.t212visualizer.dto.T212ExportRequestDto;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Profile;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;

@Service
@Profile("!demo")
public class LiveT212ExportClient implements T212ExportClient{
    private static final String EXPORTS_PATH = "/equity/history/exports";

    private final RestClient t212RestClient;
    private final RestClient downloadRestClient;

    public LiveT212ExportClient(@Qualifier("t212RestClient") RestClient t212RestClient, @Qualifier("t212DownloadRestClient") RestClient downloadRestClient) {
        this.t212RestClient = t212RestClient;
        this.downloadRestClient = downloadRestClient;
    }

    @Override
    public long requestExport(Instant from, Instant to) {
        T212ExportRequestDto request = new T212ExportRequestDto(
                new T212ExportRequestDto.DataIncluded(false, false, false, true),
                from.toString(),
                to.toString());

        T212ExportCreatedDto created = t212RestClient.post()
                .uri(EXPORTS_PATH)
                .contentType(MediaType.APPLICATION_JSON)
                .body(request)
                .retrieve()
                .body(T212ExportCreatedDto.class);

        return created.reportId();
    }

    @Override
    public List<T212ExportReportDto> fetchExports() {
        T212ExportReportDto[] response = t212RestClient.get()
                .uri(EXPORTS_PATH)
                .retrieve()
                .body(T212ExportReportDto[].class);

        return response == null ? List.of() : List.of(response);
    }

    @Override
    public String downloadExport(String url) {
        byte[] file = downloadRestClient.get()
                .uri(URI.create(url))
                .retrieve()
                .body(byte[].class);

        return new String(file, StandardCharsets.UTF_8);
    }


}

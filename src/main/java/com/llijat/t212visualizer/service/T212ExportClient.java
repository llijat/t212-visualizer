package com.llijat.t212visualizer.service;

import com.llijat.t212visualizer.dto.T212ExportReportDto;

import java.time.Instant;
import java.util.List;

public interface T212ExportClient {
    long requestExport(Instant from, Instant to);
    List<T212ExportReportDto> fetchExports();
    String downloadExport(String url);
}

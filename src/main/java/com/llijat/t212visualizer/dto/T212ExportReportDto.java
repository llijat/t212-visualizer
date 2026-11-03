package com.llijat.t212visualizer.dto;

public record T212ExportReportDto (
        long reportId,
        String status,
        String downloadLink
) {}

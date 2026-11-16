package com.llijat.t212visualizer.dto;

import java.util.Map;

public record CardExpenseImportResult (
        int imported,
        int alreadyKnown,
        int cashbackImported,
        Map<String, Long> ignoredByAction
) {}

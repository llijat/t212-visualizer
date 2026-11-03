package com.llijat.t212visualizer.dto;

import java.util.Map;

public record CardExpenseImportResult (
        int imported,
        int alreadyKnown,
        Map<String, Long> ignoredByAction
) {}

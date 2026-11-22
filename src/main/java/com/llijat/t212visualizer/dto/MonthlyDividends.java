package com.llijat.t212visualizer.dto;

import java.math.BigDecimal;
import java.time.YearMonth;

public record MonthlyDividends(
        YearMonth month,
        BigDecimal receivedEur
) {}
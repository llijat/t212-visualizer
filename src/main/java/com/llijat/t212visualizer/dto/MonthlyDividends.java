package com.llijat.t212visualizer.dto;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.List;

public record MonthlyDividends(
        YearMonth month,
        BigDecimal receivedEur,
        List<DividendPayer> payers
) {}
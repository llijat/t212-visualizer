package com.llijat.t212visualizer.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record CardExpenseSummary (
        BigDecimal total,
        long payments,
        LocalDate firstPayment,
        LocalDate lastPayment,
        BigDecimal monthlyAverage,
        BigDecimal biggestMonth,
        List<MonthlySpending> months,
        List<CategorySpending> categories,
        List<MerchantSpending> topMerchants
) {}

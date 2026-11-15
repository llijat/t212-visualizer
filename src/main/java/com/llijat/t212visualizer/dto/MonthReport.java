package com.llijat.t212visualizer.dto;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.YearMonth;
import java.util.List;

public record MonthReport(
        YearMonth month,
        boolean current,
        BigDecimal spent,
        BigDecimal previousSameDays,
        BigDecimal projected,
        List<CategorySpending> categories,
        List<MerchantSpending> topMerchants
) {
    public BigDecimal changePercent() {
        if (previousSameDays.signum() == 0) return null;
        return spent.subtract(previousSameDays)
                .multiply(BigDecimal.valueOf(100))
                .divide(previousSameDays, 1, RoundingMode.HALF_UP);
    }
}

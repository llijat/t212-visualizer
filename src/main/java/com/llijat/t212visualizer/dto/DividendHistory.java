package com.llijat.t212visualizer.dto;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Comparator;
import java.util.List;

public record DividendHistory(
        List<MonthlyDividends> months
) {
    public BigDecimal total() {
        return months.stream().map(MonthlyDividends::receivedEur).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    public BigDecimal totalIn(int year) {
        return months.stream()
                .filter(month -> month.month().getYear() == year)
                .map(MonthlyDividends::receivedEur)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    public BigDecimal best() {
        return months.stream().map(MonthlyDividends::receivedEur).max(Comparator.naturalOrder()).orElse(BigDecimal.ZERO);
    }

    /** The height of a bar: 100 for the best month, 0 for a month without dividends. */
    public int heightOf(MonthlyDividends month) {
        if (best().signum() == 0) return 0;
        return month.receivedEur().multiply(BigDecimal.valueOf(100)).divide(best(), 0, RoundingMode.HALF_UP).intValue();
    }
}
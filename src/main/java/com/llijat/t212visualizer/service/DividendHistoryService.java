package com.llijat.t212visualizer.service;

import com.llijat.t212visualizer.dto.DividendHistory;
import com.llijat.t212visualizer.dto.MonthlyDividends;
import com.llijat.t212visualizer.model.DividendEvent;
import com.llijat.t212visualizer.repository.DividendEventRepository;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class DividendHistoryService {
    private static final ZoneId SPAIN = ZoneId.of("Europe/Madrid");
    private static final int MONTHS = 12;

    private final DividendEventRepository dividendEventRepository;

    public DividendHistoryService(DividendEventRepository dividendEventRepository) {
        this.dividendEventRepository = dividendEventRepository;
    }

    public DividendHistory getLast12Months() {
        YearMonth current = YearMonth.now(SPAIN);
        YearMonth first = current.minusMonths(MONTHS - 1);

        Map<YearMonth, BigDecimal> receivedByMonth = new HashMap<>();
        for (DividendEvent dividend : dividendEventRepository.findByReceivedEurIsNotNullAndPaymentDateBetween(first.atDay(1), current.atEndOfMonth())) {
            receivedByMonth.merge(YearMonth.from(dividend.getPaymentDate()), dividend.getReceivedEur(), BigDecimal::add);
        }

        List<MonthlyDividends> months = new ArrayList<>();
        for (YearMonth month = first; !month.isAfter(current); month = month.plusMonths(1)) {
            months.add(new MonthlyDividends(month, receivedByMonth.getOrDefault(month, BigDecimal.ZERO)));
        }
        return new DividendHistory(months);
    }
}
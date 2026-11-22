package com.llijat.t212visualizer.service;

import com.llijat.t212visualizer.dto.DividendHistory;
import com.llijat.t212visualizer.dto.DividendPayer;
import com.llijat.t212visualizer.dto.MonthlyDividends;
import com.llijat.t212visualizer.model.DividendEvent;
import com.llijat.t212visualizer.repository.DividendEventRepository;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.*;

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

        Map<YearMonth, Map<String, BigDecimal>> receivedByMonthAndTicker = new HashMap<>();
        for (DividendEvent dividend : dividendEventRepository.findByReceivedEurIsNotNullAndPaymentDateBetween(first.atDay(1), current.atEndOfMonth())) {
            receivedByMonthAndTicker
                    .computeIfAbsent(YearMonth.from(dividend.getPaymentDate()), month -> new HashMap<>())
                    .merge(dividend.getTicker(), dividend.getReceivedEur(), BigDecimal::add);
        }

        List<MonthlyDividends> months = new ArrayList<>();
        for (YearMonth month = first; !month.isAfter(current); month = month.plusMonths(1)) {
            List<DividendPayer> payers = receivedByMonthAndTicker.getOrDefault(month, Map.of()).entrySet().stream()
                    .map(entry -> new DividendPayer(entry.getKey(), entry.getValue()))
                    .sorted(Comparator.comparing(DividendPayer::receivedEur).reversed())
                    .toList();
            BigDecimal total = payers.stream().map(DividendPayer::receivedEur).reduce(BigDecimal.ZERO, BigDecimal::add);
            months.add(new MonthlyDividends(month, total, payers));
        }
        return new DividendHistory(months);
    }
}
package com.llijat.t212visualizer.service;

import com.llijat.t212visualizer.dto.T212DividendDto;
import com.llijat.t212visualizer.dto.T212DividendPageDto;
import com.llijat.t212visualizer.model.DividendEvent;
import com.llijat.t212visualizer.repository.DividendEventRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;

@Service
public class DividendPaymentService {
    private static final Logger log = LoggerFactory.getLogger(DividendPaymentService.class);
    private static final String FIRST_PAGE = "/equity/history/dividends?limit=50";
    private static final String API_PREFIX = "/api/v0";
    private static final int MONTHS_BACK = 13;

    private final T212Client t212Client;
    private final DividendEventRepository dividendEventRepository;
    private final long pageDelayMs;

    /** One stock on one day: two payments of the same stock on the same day are added up. */
    private record PaymentDay(String ticker, LocalDate day) {}

    public DividendPaymentService(T212Client t212Client, DividendEventRepository dividendEventRepository,
                                  @Value("${t212.sync.page-delay-ms}") long pageDelayMs) {
        this.t212Client = t212Client;
        this.dividendEventRepository = dividendEventRepository;
        this.pageDelayMs = pageDelayMs;
    }

    @Scheduled(fixedRate = 6*60*60*1000, initialDelay = 7*60*1000)
    public void importPayments() {
        LocalDate from = LocalDate.now().minusMonths(MONTHS_BACK);
        Map<PaymentDay, BigDecimal> receivedByDay = new HashMap<>();
        Map<PaymentDay, BigDecimal> grossByDay = new HashMap<>();

        try {
            String path = FIRST_PAGE;
            boolean reachedOld = false;
            while (path != null && !reachedOld) {
                T212DividendPageDto page = t212Client.fetchDividends(path);
                for (T212DividendDto payment : page.items()) {
                    if (payment.paidDay().isBefore(from)) {
                        reachedOld = true;
                        break;
                    }
                    if (payment.amountInEuro() == null) continue;
                    PaymentDay key = new PaymentDay(payment.ticker(), payment.paidDay());
                    receivedByDay.merge(key, payment.amountInEuro(), BigDecimal::add);
                    if (payment.grossAmountPerShare() != null) grossByDay.merge(key, payment.grossAmountPerShare(), BigDecimal::add);
                }

                if (page.nextPagePath() == null) path = null;
                else path = page.nextPagePath().substring(API_PREFIX.length());

                if (path != null && !reachedOld) Thread.sleep(pageDelayMs);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return;
        } catch (Exception e) {
            log.warn("Could not import the dividend payments: {}", e.getMessage());
            return;
        }

        for (Map.Entry<PaymentDay, BigDecimal> entry : receivedByDay.entrySet()) {
            save(entry.getKey(), entry.getValue(), grossByDay.get(entry.getKey()));
        }
        log.info("Recorded {} dividend payments.", receivedByDay.size());
    }

    private void save(PaymentDay key, BigDecimal receivedEur, BigDecimal grossAmountPerShare) {
        DividendEvent event = dividendEventRepository.findByTickerAndPaymentDate(key.ticker(), key.day())
                .orElseGet(() -> new DividendEvent(key.ticker(), key.day()));
        event.recordPayment(receivedEur, grossAmountPerShare);
        dividendEventRepository.save(event);
    }
}
package com.llijat.t212visualizer.service;

import com.llijat.t212visualizer.dto.AlphaVantageDividendDto;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Made-up quarterly dividends for the invented companies in DemoT212Client, always the same ones relative to today.
 * With the demo companies: Globex pays in 4 days, Acme and Initech in 11, and Hooli never pays, like a real growth stock.
 */
@Service
@Profile("demo")
public class DemoAlphaVantageClient implements AlphaVantageClient {

    @Override
    public List<AlphaVantageDividendDto> fetchDividends(String symbol) {
        if (!pays(symbol)) return List.of();

        String amount = amountPerShare(symbol).toPlainString();
        // Newest first, as Alpha Vantage sends them: the announced one and the three before it
        List<AlphaVantageDividendDto> dividends = new ArrayList<>();
        for (int quarter = 0; quarter < 4; quarter++) {
            LocalDate payment = nextPayment(symbol).minusMonths(3L * quarter);
            dividends.add(new AlphaVantageDividendDto(payment.minusDays(14).toString(), payment.toString(), amount));
        }
        return dividends;
    }

    // Shared with DemoT212Client, so the demo's received payments fall on the same days as these announcements

    static boolean pays(String symbol) {
        return Math.floorMod(symbol.hashCode(), 3) != 0;
    }

    static LocalDate nextPayment(String symbol) {
        return LocalDate.now().plusDays(Math.floorMod(symbol.hashCode(), 30) + 3);
    }

    static BigDecimal amountPerShare(String symbol) {
        return BigDecimal.valueOf(10 + Math.floorMod(symbol.hashCode(), 90), 2);
    }
}

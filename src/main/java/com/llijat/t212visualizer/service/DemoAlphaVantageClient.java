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
        int seed = symbol.hashCode();
        if (Math.floorMod(seed, 3) == 0) return List.of();

        LocalDate nextPayment = LocalDate.now().plusDays(Math.floorMod(seed, 30) + 3);
        String amount = BigDecimal.valueOf(10 + Math.floorMod(seed, 90), 2).toPlainString();

        // Newest first, as Alpha Vantage sends them: the announced one and the three before it
        List<AlphaVantageDividendDto> dividends = new ArrayList<>();
        for (int quarter = 0; quarter < 4; quarter++) {
            LocalDate payment = nextPayment.minusMonths(3L * quarter);
            dividends.add(new AlphaVantageDividendDto(payment.minusDays(14).toString(), payment.toString(), amount));
        }
        return dividends;
    }
}

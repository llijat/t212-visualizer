package com.llijat.t212visualizer.service;

import com.llijat.t212visualizer.dto.AlphaVantageDividendDto;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.TreeMap;

/**
 * Made-up quarterly dividends for the invented companies in DemoT212Client, always the same ones relative to today.
 * With the demo companies: Globex pays in 4 days, Acme and Initech in 11, and Hooli never pays, like a real growth stock.
 * The daily closes are a made-up walk that ends at 100, the price DemoFinnhubClient gives today, so the mini charts do not jump.
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

    private static final int TRADING_DAYS = 100;   // what "compact" returns

    @Override
    public Map<LocalDate, BigDecimal> fetchDailyCloses(String symbol) {
        // Walked backwards from 100: each earlier day undoes a move of up to 2 %, plus a trend of its own per symbol.
        // The same symbol always gets the same walk, so the demo looks the same every time it starts
        Random random = new Random(symbol.hashCode());
        double trend = (Math.floorMod(symbol.hashCode(), 7) - 3) * 0.0008;
        Map<LocalDate, BigDecimal> closes = new TreeMap<>();
        double price = 100;
        LocalDate day = LocalDate.now().minusDays(1);
        while (closes.size() < TRADING_DAYS) {
            if (day.getDayOfWeek() != DayOfWeek.SATURDAY && day.getDayOfWeek() != DayOfWeek.SUNDAY) {
                closes.put(day, BigDecimal.valueOf(price).setScale(4, RoundingMode.HALF_UP));
                price = price / (1 + trend + (random.nextDouble() - 0.5) * 0.04);
            }
            day = day.minusDays(1);
        }
        return closes;
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

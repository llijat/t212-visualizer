package com.llijat.t212visualizer.service;

import com.llijat.t212visualizer.dto.T212AccountSummaryDto;
import com.llijat.t212visualizer.dto.T212PositionDto;
import com.llijat.t212visualizer.dto.T212TransactionDto;
import com.llijat.t212visualizer.dto.T212TransactionPageDto;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

/** Empresas y cifras inventadas, para probar la app sin una cuenta de T212. */
@Service
@Profile("demo")
public class DemoT212Client implements T212Client {

    private static final BigDecimal REALIZED_PROFIT = new BigDecimal("120.00");

    private static final List<T212PositionDto> POSITIONS = List.of(
            position("ACME_US_EQ", "Acme Corp", "USD", "12.5", "98.40", "112.30", "1100.00", "1250.00", "-8.50"),
            position("GLBX_US_EQ", "Globex Inc", "USD", "4.2", "310.00", "342.75", "1180.00", "1290.00", "-11.20"),
            position("INIT_US_EQ", "Initech", "USD", "20", "41.10", "38.60", "750.00", "700.00", "-6.00"),
            position("HOOL_US_EQ", "Hooli", "USD", "1.75", "520.00", "601.20", "830.00", "955.00", "-7.25"),
            position("WAYN_EU_EQ", "Wayne Enterprises", "EUR", "8", "75.00", "82.50", "600.00", "660.00", null)
    );

    // Del más nuevo al más viejo, como la API real
    private static final List<T212TransactionDto> TRANSACTIONS = List.of(
            transaction("demo-7", "INTEREST_ON_FREE_CASH", "2.35", 1),
            transaction("demo-6", "WITHDRAW", "-45.90", 3),
            transaction("demo-5", "DEPOSIT", "0.69", 4), // cashback: menos de 5 €
            transaction("demo-4", "DEPOSIT", "500.00", 30),
            transaction("demo-3", "FEE", "-0.40", 60),
            transaction("demo-2", "DEPOSIT", "1500.00", 120),
            transaction("demo-1", "DEPOSIT", "4000.00", 200)
    );

    @Override
    public List<T212PositionDto> fetchPositions() {
        return POSITIONS;
    }

    /** El efectivo y el total se calculan, para que las cifras siempre cuadren con posiciones y movimientos. */
    @Override
    public T212AccountSummaryDto fetchAccountSummary() {
        BigDecimal currentValue = sum(POSITIONS.stream().map(p -> p.walletImpact().currentValue()).toList());
        BigDecimal totalCost = sum(POSITIONS.stream().map(p -> p.walletImpact().totalCost()).toList());
        BigDecimal unrealized = currentValue.subtract(totalCost);
        BigDecimal movements = sum(TRANSACTIONS.stream().map(T212TransactionDto::amount).toList());
        BigDecimal cash = movements.add(REALIZED_PROFIT).subtract(totalCost);

        return new T212AccountSummaryDto(
                "EUR",
                1L,
                cash.add(currentValue),
                new T212AccountSummaryDto.Cash(cash, BigDecimal.ZERO, BigDecimal.ZERO),
                new T212AccountSummaryDto.Investments(currentValue, totalCost, REALIZED_PROFIT, unrealized)
        );
    }

    @Override
    public T212TransactionPageDto fetchTransactions(String path) {
        return new T212TransactionPageDto(TRANSACTIONS, null);
    }

    private static BigDecimal sum(List<BigDecimal> values) {
        return values.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private static T212PositionDto position(String ticker, String name, String currency, String quantity,
                                            String averagePrice, String currentPrice, String totalCost,
                                            String currentValue, String fxImpact) {
        BigDecimal cost = new BigDecimal(totalCost);
        BigDecimal value = new BigDecimal(currentValue);
        return new T212PositionDto(
                new BigDecimal(averagePrice),
                "2026-01-15T10:00:00Z",
                new BigDecimal(currentPrice),
                new BigDecimal(quantity),
                new BigDecimal(quantity),
                BigDecimal.ZERO,
                new T212PositionDto.Instrument(currency, "DEMO", name, ticker),
                new T212PositionDto.WalletImpact(
                        "EUR",
                        value,
                        fxImpact == null ? null : new BigDecimal(fxImpact),
                        cost,
                        value.subtract(cost))
        );
    }

    private static T212TransactionDto transaction(String reference, String type, String amount, int daysAgo) {
        String dateTime = Instant.now().minus(Duration.ofDays(daysAgo)).toString();
        return new T212TransactionDto(new BigDecimal(amount), "EUR", dateTime, reference, type);
    }
}

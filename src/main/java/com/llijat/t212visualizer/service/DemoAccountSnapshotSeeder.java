package com.llijat.t212visualizer.service;

import com.llijat.t212visualizer.dto.T212TransactionDto;
import com.llijat.t212visualizer.model.AccountSnapshot;
import com.llijat.t212visualizer.repository.AccountSnapshotRepository;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeMap;

/**
 * Invented daily snapshots, so the account chart has a past in demo mode.
 * They follow the demo's own deposits and end close to its account value, so the chart, "today" and the totals agree.
 */
@Component
@Profile("demo")
public class DemoAccountSnapshotSeeder implements ApplicationRunner {

    // The same rule as the net deposits of the real app: card cashback (under 5 €) is not money put in
    private static final BigDecimal CASHBACK_THRESHOLD = new BigDecimal("5.0");

    private final T212Client t212Client;
    private final AccountSnapshotRepository accountSnapshotRepository;
    private final Clock clock;

    public DemoAccountSnapshotSeeder(T212Client t212Client, AccountSnapshotRepository accountSnapshotRepository, Clock clock) {
        this.t212Client = t212Client;
        this.accountSnapshotRepository = accountSnapshotRepository;
        this.clock = clock;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (accountSnapshotRepository.count() > 0) return;
        accountSnapshotRepository.saveAll(buildSnapshots(LocalDate.now(clock)));
    }

    /** One snapshot a day from the first deposit up to yesterday (today's is taken by the app itself) */
    List<AccountSnapshot> buildSnapshots(LocalDate today) {
        // Net deposits by day
        TreeMap<LocalDate, BigDecimal> flows = new TreeMap<>();
        for (T212TransactionDto transaction : t212Client.fetchTransactions(null).items()) {
            boolean deposit = "DEPOSIT".equals(transaction.type()) && transaction.amount().compareTo(CASHBACK_THRESHOLD) >= 0;
            boolean withdrawal = "WITHDRAW".equals(transaction.type()) || "TRANSFER".equals(transaction.type());
            if (!deposit && !withdrawal) continue;
            LocalDate day = Instant.parse(transaction.dateTime()).atZone(clock.getZone()).toLocalDate();
            flows.merge(day, transaction.amount(), BigDecimal::add);
        }
        if (flows.isEmpty()) return List.of();

        LocalDate first = flows.firstKey();
        LocalDate yesterday = today.minusDays(1);
        long days = ChronoUnit.DAYS.between(first, today);

        BigDecimal finalDeposits = flows.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        double endGrowth = t212Client.fetchAccountSummary().totalValue().doubleValue() / finalDeposits.doubleValue();

        List<AccountSnapshot> snapshots = new ArrayList<>();
        BigDecimal deposits = BigDecimal.ZERO;
        for (LocalDate day = first; !day.isAfter(yesterday); day = day.plusDays(1)) {
            deposits = deposits.add(flows.getOrDefault(day, BigDecimal.ZERO));
            double x = ChronoUnit.DAYS.between(first, day) / (double) days;   // 0 on the first day, almost 1 yesterday
            // A slow climb to today's growth, a dip in the middle and some daily noise, all fixed so the demo looks the same every time
            double growth = 1 + (endGrowth - 1) * x - 0.06 * Math.sin(Math.PI * x) * Math.sin(2.2 * Math.PI * x) + 0.008 * Math.sin(day.toEpochDay() * 1.7);
            BigDecimal value = deposits.multiply(BigDecimal.valueOf(growth)).setScale(2, RoundingMode.HALF_UP);
            snapshots.add(new AccountSnapshot(day, value, deposits, day.atTime(22, 0).atZone(clock.getZone()).toInstant()));
        }
        return snapshots;
    }
}

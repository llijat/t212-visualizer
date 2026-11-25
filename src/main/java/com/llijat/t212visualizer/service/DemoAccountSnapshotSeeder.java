package com.llijat.t212visualizer.service;

import com.llijat.t212visualizer.dto.T212TransactionDto;
import com.llijat.t212visualizer.model.AccountSnapshot;
import com.llijat.t212visualizer.model.AccountTick;
import com.llijat.t212visualizer.repository.AccountSnapshotRepository;
import com.llijat.t212visualizer.repository.AccountTickRepository;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.TreeMap;

/**
 * Invented daily snapshots, so the account chart has a past in demo mode.
 * They follow the demo's own deposits and end close to its account value, so the chart, "today" and the totals agree.
 * Also a week of ticks every 15 minutes, for the 1D and 1W charts: they move while the US market is open and stay still the rest of the day.
 */
@Component
@Profile("demo")
public class DemoAccountSnapshotSeeder implements ApplicationRunner {

    // The same rule as the net deposits of the real app: card cashback (under 5 €) is not money put in
    private static final BigDecimal CASHBACK_THRESHOLD = new BigDecimal("5.0");

    private final T212Client t212Client;
    private static final Duration TICK = Duration.ofMinutes(15);
    private static final int TICK_DAYS = 7;
    // The US session in Spain: 15:30 to 22:00
    private static final int MARKET_OPENS = 15 * 60 + 30;
    private static final int MARKET_MINUTES = 390;

    private final AccountSnapshotRepository accountSnapshotRepository;
    private final AccountTickRepository accountTickRepository;
    private final Clock clock;

    public DemoAccountSnapshotSeeder(T212Client t212Client, AccountSnapshotRepository accountSnapshotRepository, AccountTickRepository accountTickRepository, Clock clock) {
        this.t212Client = t212Client;
        this.accountSnapshotRepository = accountSnapshotRepository;
        this.accountTickRepository = accountTickRepository;
        this.clock = clock;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (accountSnapshotRepository.count() > 0) return;
        List<AccountSnapshot> snapshots = buildSnapshots(LocalDate.now(clock));
        accountSnapshotRepository.saveAll(snapshots);
        accountTickRepository.saveAll(buildTicks(snapshots, Instant.now(clock)));
    }

    /**
     * A tick every 15 minutes of the last 7 days, up to now (the next one is taken by the app itself).
     * Each day goes from the day before's close to its own, during the US session; today ends at the account's value now.
     */
    List<AccountTick> buildTicks(List<AccountSnapshot> snapshots, Instant now) {
        if (snapshots.isEmpty()) return List.of();
        Map<LocalDate, AccountSnapshot> byDay = snapshots.stream().collect(Collectors.toMap(AccountSnapshot::getSnapshotDate, Function.identity()));
        AccountSnapshot last = snapshots.get(snapshots.size() - 1);
        BigDecimal valueNow = t212Client.fetchAccountSummary().totalValue();

        List<AccountTick> ticks = new ArrayList<>();
        Instant end = now.truncatedTo(ChronoUnit.MINUTES).minus(Duration.ofMinutes(now.atZone(clock.getZone()).getMinute() % 15));
        for (Instant at = end.minus(Duration.ofDays(TICK_DAYS)); at.isBefore(end); at = at.plus(TICK)) {
            ZonedDateTime local = at.atZone(clock.getZone());
            AccountSnapshot close = byDay.get(local.toLocalDate());
            AccountSnapshot previous = byDay.get(local.toLocalDate().minusDays(1));
            if (previous == null) continue;   // before the demo's first day

            double from = previous.getTotalValue().doubleValue();
            double to = close != null ? close.getTotalValue().doubleValue() : valueNow.doubleValue();   // no close yet: today
            BigDecimal deposits = close != null ? close.getNetDeposits() : last.getNetDeposits();
            // How far into the session: 0 before it opens, 1 after it closes. Some noise while it is open, always the same
            int minute = local.getHour() * 60 + local.getMinute();
            double session = Math.clamp((minute - MARKET_OPENS) / (double) MARKET_MINUTES, 0, 1);
            double noise = 0.003 * Math.sin(Math.PI * session) * Math.sin(at.getEpochSecond() / 3600.0 * 1.9);
            double value = (from + (to - from) * session) * (1 + noise);
            ticks.add(new AccountTick(at, BigDecimal.valueOf(value).setScale(2, RoundingMode.HALF_UP), deposits));
        }
        return ticks;
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

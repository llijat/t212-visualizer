package com.llijat.t212visualizer.service;

import com.llijat.t212visualizer.dto.AccountPoint;
import com.llijat.t212visualizer.dto.AccountTickPoint;
import com.llijat.t212visualizer.dto.DailyChange;
import com.llijat.t212visualizer.dto.T212AccountSummaryDto;
import com.llijat.t212visualizer.model.AccountSnapshot;
import com.llijat.t212visualizer.model.AccountTick;
import com.llijat.t212visualizer.repository.AccountSnapshotRepository;
import com.llijat.t212visualizer.repository.AccountTickRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Service
public class AccountSnapshotService {
    private static final Logger log = LoggerFactory.getLogger(AccountSnapshotService.class);
    private final Clock clock;
    private static final Duration RECENT = Duration.ofDays(8);   // a week, plus a day so 1W starts at a close

    private final AccountService accountService;
    private final TransactionService transactionService;
    private final AccountSnapshotRepository accountSnapshotRepository;
    private final AccountTickRepository accountTickRepository;

    public AccountSnapshotService(AccountService accountService, TransactionService transactionService, AccountSnapshotRepository accountSnapshotRepository, Clock clock, AccountTickRepository accountTickRepository) {
        this.accountService = accountService;
        this.transactionService = transactionService;
        this.accountSnapshotRepository = accountSnapshotRepository;
        this.clock = clock;
        this.accountTickRepository = accountTickRepository;
    }

    /** What the account made today, without the money put in or taken out today. Null until there is an earlier day to compare with. */
    public DailyChange getTodayChange() {
        T212AccountSummaryDto summary = accountService.getSummary();
        if (summary == null || !transactionService.isFirstSyncDone()) return null;

        AccountSnapshot lastClose = accountSnapshotRepository.findFirstBySnapshotDateBeforeOrderBySnapshotDateDesc(LocalDate.now(clock)).orElse(null);
        if (lastClose == null) return null;

        BigDecimal depositedToday = transactionService.getNetDeposits().subtract(lastClose.getNetDeposits());
        BigDecimal startOfDay = lastClose.getTotalValue().add(depositedToday);
        BigDecimal amount = summary.totalValue().subtract(startOfDay);
        if (startOfDay.signum() <= 0) return new DailyChange(amount, BigDecimal.ZERO);

        BigDecimal percent = amount.multiply(BigDecimal.valueOf(100)).divide(startOfDay, 2, RoundingMode.HALF_UP);
        return new DailyChange(amount, percent);
    }

    public List<AccountPoint> getHistory() {
        List<AccountPoint> points = new ArrayList<>();
        BigDecimal growth = BigDecimal.ONE;
        AccountSnapshot previous = null;

        for (AccountSnapshot day : accountSnapshotRepository.findAllByOrderBySnapshotDateAsc()) {
            if (previous != null) {
                BigDecimal deposited = day.getNetDeposits().subtract(previous.getNetDeposits());
                BigDecimal start = previous.getTotalValue().add(deposited);
                if (start.signum() > 0) growth = growth.multiply(day.getTotalValue()).divide(start, 10, RoundingMode.HALF_UP);
            }
            points.add(new AccountPoint(day.getSnapshotDate(), day.getTotalValue(), day.getNetDeposits(), growth));
            previous = day;
        }
        return points;
    }

    /** The ticks of the last days, for the 1D and 1W charts, with the growth chained tick after tick (TWR). */
    public List<AccountTickPoint> getRecentTicks() {
        List<AccountTickPoint> points = new ArrayList<>();
        BigDecimal growth = BigDecimal.ONE;
        AccountTick previous = null;

        for (AccountTick tick : accountTickRepository.findByTakenAtGreaterThanEqualOrderByTakenAtAsc(Instant.now(clock).minus(RECENT))) {
            if (previous != null) {
                BigDecimal deposited = tick.getNetDeposits().subtract(previous.getNetDeposits());
                BigDecimal start = previous.getTotalValue().add(deposited);
                if (start.signum() > 0) growth = growth.multiply(tick.getTotalValue()).divide(start, 10, RoundingMode.HALF_UP);
            }
            points.add(new AccountTickPoint(tick.getTakenAt(), tick.getTotalValue(), tick.getNetDeposits(), growth));
            previous = tick;
        }
        return points;
    }


    @Scheduled(fixedRate = 15*60*1000, initialDelay = 10*60*1000)
    public void takeSnapshot() {
        Instant now = Instant.now(clock);
        T212AccountSummaryDto summary = accountService.getSummary();
        if (summary == null || !transactionService.isFirstSyncDone()) {
            log.info("Account snapshot skipped: the account or its movements are not loaded yet.");
            return;
        }

        try {
            LocalDate today = LocalDate.now(clock);
            BigDecimal value = summary.totalValue();
            BigDecimal netDeposits = transactionService.getNetDeposits();

            accountSnapshotRepository.save(new AccountSnapshot(today, value, netDeposits, now));
            accountTickRepository.save(new AccountTick(now, value, netDeposits));
            log.info("Saved the account snapshot of {}.", today);
        }
        catch (RuntimeException e) {
            log.warn("Could not save the account snapshot: {}", e.getMessage());
        }
    }


}

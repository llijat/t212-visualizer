package com.llijat.t212visualizer.service;

import com.llijat.t212visualizer.dto.DailyChange;
import com.llijat.t212visualizer.dto.T212AccountSummaryDto;
import com.llijat.t212visualizer.model.AccountSnapshot;
import com.llijat.t212visualizer.repository.AccountSnapshotRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

@Service
public class AccountSnapshotService {
    private static final Logger log = LoggerFactory.getLogger(AccountSnapshotService.class);
    private static final ZoneId SPAIN = ZoneId.of("Europe/Madrid");

    private final AccountService accountService;
    private final TransactionService transactionService;
    private final AccountSnapshotRepository accountSnapshotRepository;

    public AccountSnapshotService(AccountService accountService, TransactionService transactionService, AccountSnapshotRepository accountSnapshotRepository) {
        this.accountService = accountService;
        this.transactionService = transactionService;
        this.accountSnapshotRepository = accountSnapshotRepository;
    }

    /** What the account made today, without the money put in or taken out today. Null until there is an earlier day to compare with. */
    public DailyChange getTodayChange() {
        T212AccountSummaryDto summary = accountService.getSummary();
        if (summary == null || !transactionService.isFirstSyncDone()) return null;

        AccountSnapshot lastClose = accountSnapshotRepository.findFirstBySnapshotDateBeforeOrderBySnapshotDateDesc(LocalDate.now(SPAIN)).orElse(null);
        if (lastClose == null) return null;

        BigDecimal depositedToday = transactionService.getNetDeposits().subtract(lastClose.getNetDeposits());
        BigDecimal startOfDay = lastClose.getTotalValue().add(depositedToday);
        BigDecimal amount = summary.totalValue().subtract(startOfDay);
        if (startOfDay.signum() <= 0) return new DailyChange(amount, BigDecimal.ZERO);

        BigDecimal percent = amount.multiply(BigDecimal.valueOf(100)).divide(startOfDay, 2, RoundingMode.HALF_UP);
        return new DailyChange(amount, percent);
    }

    @Scheduled(fixedRate = 60*60*1000, initialDelay = 10*60*1000)
    public void takeSnapshot() {
        T212AccountSummaryDto summary = accountService.getSummary();
        if (summary == null || !transactionService.isFirstSyncDone()) {
            log.info("Account snapshot skipped: the account or its movements are not loaded yet.");
            return;
        }

        try {
            LocalDate today = LocalDate.now(SPAIN);
            accountSnapshotRepository.save(new AccountSnapshot(today, summary.totalValue(),transactionService.getNetDeposits(),Instant.now()));
            log.info("Saved the account snapshot of {}.", today);
        }
        catch (RuntimeException e) {
            log.warn("Could not save the account snapshot: {}", e.getMessage());
        }
    }
}

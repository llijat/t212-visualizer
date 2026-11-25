package com.llijat.t212visualizer.service;

import com.llijat.t212visualizer.dto.T212AccountSummaryDto;
import com.llijat.t212visualizer.dto.T212TransactionDto;
import com.llijat.t212visualizer.dto.T212TransactionPageDto;
import com.llijat.t212visualizer.model.AccountSnapshot;
import com.llijat.t212visualizer.model.AccountTick;
import com.llijat.t212visualizer.repository.AccountSnapshotRepository;
import com.llijat.t212visualizer.repository.AccountTickRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DemoAccountSnapshotSeederTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-11-15T11:00:00Z"), ZoneId.of("Europe/Madrid"));
    private static final LocalDate TODAY = LocalDate.of(2026, 11, 15);

    @Mock
    private T212Client t212Client;
    @Mock
    private AccountSnapshotRepository repository;
    @Mock
    private AccountTickRepository tickRepository;

    private DemoAccountSnapshotSeeder seeder;

    @BeforeEach
    void setUp() {
        seeder = new DemoAccountSnapshotSeeder(t212Client, repository, tickRepository, CLOCK);
    }

    private static T212TransactionDto movement(String type, String amount, String day) {
        return new T212TransactionDto(new BigDecimal(amount), "EUR", day + "T10:00:00Z", type + day, type);
    }

    private void theDemoAccountHas(String value, T212TransactionDto... movements) {
        when(t212Client.fetchTransactions(any())).thenReturn(new T212TransactionPageDto(List.of(movements), null));
        when(t212Client.fetchAccountSummary()).thenReturn(new T212AccountSummaryDto("EUR", 1L, new BigDecimal(value), null, null));
    }

    @Test
    void thereIsASnapshotEveryDay_fromTheFirstDeposit_upToYesterday() {
        theDemoAccountHas("1100", movement("DEPOSIT", "1000", "2026-10-16"));

        List<AccountSnapshot> snapshots = seeder.buildSnapshots(TODAY);

        assertEquals(LocalDate.of(2026, 10, 16), snapshots.get(0).getSnapshotDate());
        assertEquals(TODAY.minusDays(1), snapshots.get(snapshots.size() - 1).getSnapshotDate());
        assertEquals(30, snapshots.size());
    }

    @Test
    void theDeposits_followTheMovements_withoutTheCashback() {
        theDemoAccountHas("2000",
                movement("DEPOSIT", "1000", "2026-10-16"),
                movement("DEPOSIT", "0.69", "2026-10-20"),      // card cashback, not money put in
                movement("DEPOSIT", "500", "2026-11-01"),
                movement("WITHDRAW", "-100", "2026-11-10"),
                movement("INTEREST_ON_FREE_CASH", "2.35", "2026-11-12"));

        List<AccountSnapshot> snapshots = seeder.buildSnapshots(TODAY);

        assertEquals(new BigDecimal("1000"), depositsOn(snapshots, "2026-10-31"));
        assertEquals(new BigDecimal("1500"), depositsOn(snapshots, "2026-11-01"));
        assertEquals(new BigDecimal("1400"), depositsOn(snapshots, "2026-11-14"));
    }

    @Test
    void yesterday_isCloseToTheDemoAccountValue_soTodayIsASmallMove() {
        theDemoAccountHas("1100", movement("DEPOSIT", "1000", "2026-10-16"));

        List<AccountSnapshot> snapshots = seeder.buildSnapshots(TODAY);

        BigDecimal yesterday = snapshots.get(snapshots.size() - 1).getTotalValue();
        assertTrue(yesterday.subtract(new BigDecimal("1100")).abs().compareTo(new BigDecimal("15")) < 0, "yesterday was " + yesterday);
    }

    @Test
    void withSnapshotsAlready_nothingIsAdded() {
        when(repository.count()).thenReturn(30L);

        seeder.run(null);

        verify(repository, never()).saveAll(anyList());
        verify(tickRepository, never()).saveAll(anyList());
    }

    // ===== Ticks every 15 minutes, for the 1D and 1W charts =====

    private static final Instant NOW = Instant.parse("2026-11-15T16:07:00Z");   // 17:07 in Madrid, a Sunday: no matter for the demo

    private List<AccountTick> aWeekOfTicks() {
        theDemoAccountHas("1100", movement("DEPOSIT", "1000", "2026-10-16"));
        return seeder.buildTicks(seeder.buildSnapshots(TODAY), NOW);
    }

    @Test
    void theTicks_areEvery15Minutes_forAWeek_upToTheLastQuarterBeforeNow() {
        List<AccountTick> ticks = aWeekOfTicks();

        assertEquals(7 * 24 * 4, ticks.size());
        assertEquals(Instant.parse("2026-11-15T16:00:00Z").minus(Duration.ofMinutes(15)), ticks.get(ticks.size() - 1).getTakenAt());
        assertEquals(Duration.ofMinutes(15), Duration.between(ticks.get(0).getTakenAt(), ticks.get(1).getTakenAt()));
    }

    @Test
    void beforeTheUsMarketOpens_theValueIsStill_atYesterdaysClose() {
        theDemoAccountHas("1100", movement("DEPOSIT", "1000", "2026-10-16"));
        BigDecimal yesterdaysClose = seeder.buildSnapshots(TODAY).get(29).getTotalValue();

        List<AccountTick> ticks = seeder.buildTicks(seeder.buildSnapshots(TODAY), NOW);

        AccountTick morning = ticks.stream().filter(t -> t.getTakenAt().equals(Instant.parse("2026-11-15T09:00:00Z"))).findFirst().orElseThrow();
        assertEquals(yesterdaysClose, morning.getTotalValue());
    }

    @Test
    void withoutSnapshots_thereAreNoTicks() {
        assertEquals(List.of(), seeder.buildTicks(List.of(), NOW));
    }

    private static BigDecimal depositsOn(List<AccountSnapshot> snapshots, String day) {
        return snapshots.stream().filter(s -> s.getSnapshotDate().equals(LocalDate.parse(day))).findFirst().orElseThrow().getNetDeposits();
    }
}

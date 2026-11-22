package com.llijat.t212visualizer.service;

import java.time.Clock;

import com.llijat.t212visualizer.dto.AccountPoint;
import com.llijat.t212visualizer.dto.DailyChange;
import com.llijat.t212visualizer.dto.T212AccountSummaryDto;
import com.llijat.t212visualizer.model.AccountSnapshot;
import com.llijat.t212visualizer.repository.AccountSnapshotRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AccountSnapshotServiceTest {

    // "Now" for the code under test: fixed, so a test run at midnight does not see two different days
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-11-15T11:00:00Z"), ZoneId.of("Europe/Madrid"));

    @Mock
    private AccountService accountService;
    @Mock
    private TransactionService transactionService;
    @Mock
    private AccountSnapshotRepository repository;

    private AccountSnapshotService service;

    @BeforeEach
    void setUp() {
        service = new AccountSnapshotService(accountService, transactionService, repository, CLOCK);
    }

    // Only the total value matters for the snapshot
    private static T212AccountSummaryDto accountWorth(String euros) {
        return new T212AccountSummaryDto("EUR", 1L, new BigDecimal(euros), null, null);
    }

    @Test
    void withoutTheAccount_nothingIsSaved() {
        when(accountService.getSummary()).thenReturn(null);   // T212 did not answer

        service.takeSnapshot();

        verify(repository, never()).save(any());
    }

    @Test
    void beforeTheMovementsAreLoaded_nothingIsSaved_soTheDepositsAreNotZero() {
        when(accountService.getSummary()).thenReturn(accountWorth("9000"));
        when(transactionService.isFirstSyncDone()).thenReturn(false);

        service.takeSnapshot();

        verify(repository, never()).save(any());
    }

    @Test
    void theSnapshot_isToday_withTheValueAndTheNetDeposits() {
        when(accountService.getSummary()).thenReturn(accountWorth("9000.50"));
        when(transactionService.isFirstSyncDone()).thenReturn(true);
        when(transactionService.getNetDeposits()).thenReturn(new BigDecimal("8500"));

        service.takeSnapshot();

        ArgumentCaptor<AccountSnapshot> saved = ArgumentCaptor.forClass(AccountSnapshot.class);
        verify(repository).save(saved.capture());
        assertEquals(LocalDate.now(CLOCK), saved.getValue().getSnapshotDate());
        assertEquals(new BigDecimal("9000.50"), saved.getValue().getTotalValue());
        assertEquals(new BigDecimal("8500"), saved.getValue().getNetDeposits());
        assertNotNull(saved.getValue().getTakenAt());
    }

    @Test
    void ifTheDatabaseFails_theErrorDoesNotEscape_soTheNextHourTriesAgain() {
        when(accountService.getSummary()).thenReturn(accountWorth("9000"));
        when(transactionService.isFirstSyncDone()).thenReturn(true);
        when(transactionService.getNetDeposits()).thenReturn(new BigDecimal("8500"));
        when(repository.save(any())).thenThrow(new RuntimeException("database is down"));

        assertDoesNotThrow(() -> service.takeSnapshot());
    }

    // ===== What the account made today =====

    private static final LocalDate TODAY = LocalDate.now(CLOCK);

    // The latest snapshot before today: yesterday's close, or Friday's on a Monday
    private void theLastCloseWas(String value, String netDeposits) {
        when(repository.findFirstBySnapshotDateBeforeOrderBySnapshotDateDesc(TODAY))
                .thenReturn(Optional.of(new AccountSnapshot(TODAY.minusDays(1), new BigDecimal(value), new BigDecimal(netDeposits), Instant.now())));
    }

    private void nowTheAccountIs(String value, String netDeposits) {
        when(accountService.getSummary()).thenReturn(accountWorth(value));
        when(transactionService.isFirstSyncDone()).thenReturn(true);
        when(transactionService.getNetDeposits()).thenReturn(new BigDecimal(netDeposits));
    }

    @Test
    void today_isTheChangeSinceTheLastClose() {
        theLastCloseWas("9000.00", "8500.00");
        nowTheAccountIs("9090.00", "8500.00");

        DailyChange today = service.getTodayChange();

        assertEquals(new BigDecimal("90.00"), today.amount());
        assertEquals(new BigDecimal("1.00"), today.percent());   // 90 of the 9000 there was this morning
    }

    @Test
    void moneyPutInToday_isNotAGain() {
        theLastCloseWas("9000.00", "8500.00");
        nowTheAccountIs("9540.00", "9000.00");   // 500 deposited today, so only 40 is what the market did

        DailyChange today = service.getTodayChange();

        assertEquals(new BigDecimal("40.00"), today.amount());
        assertEquals(new BigDecimal("0.42"), today.percent());   // 40 of 9500 = 0.421...
    }

    @Test
    void moneyTakenOutToday_isNotALoss() {
        theLastCloseWas("9000.00", "8500.00");
        nowTheAccountIs("8470.00", "8000.00");   // 500 withdrawn today: the market lost 30

        assertEquals(new BigDecimal("-30.00"), service.getTodayChange().amount());
    }

    @Test
    void withoutAnEarlierSnapshot_thereIsNoToday() {
        when(accountService.getSummary()).thenReturn(accountWorth("9000"));
        when(transactionService.isFirstSyncDone()).thenReturn(true);
        when(repository.findFirstBySnapshotDateBeforeOrderBySnapshotDateDesc(TODAY)).thenReturn(Optional.empty());

        assertNull(service.getTodayChange());
    }

    @Test
    void beforeTheAccountIsLoaded_thereIsNoToday() {
        when(accountService.getSummary()).thenReturn(null);

        assertNull(service.getTodayChange());
    }

    @Test
    void anEmptyAccountThisMorning_hasNoPercent_insteadOfDividingByZero() {
        theLastCloseWas("0", "0");
        nowTheAccountIs("0", "0");

        DailyChange today = service.getTodayChange();

        assertEquals(new BigDecimal("0"), today.amount());
        assertEquals(BigDecimal.ZERO, today.percent());
    }

    // ===== The chart: every snapshot, with the growth chained day after day (TWR) =====

    private static AccountSnapshot day(int dayOfMonth, String value, String netDeposits) {
        return new AccountSnapshot(LocalDate.of(2026, 11, dayOfMonth), new BigDecimal(value), new BigDecimal(netDeposits), Instant.now());
    }

    private List<AccountPoint> historyOf(AccountSnapshot... days) {
        when(repository.findAllByOrderBySnapshotDateAsc()).thenReturn(List.of(days));
        return service.getHistory();
    }

    // compareTo and not equals: 0.99 and 0.9900000000 are the same growth with different decimals
    private static void assertGrowth(String expected, AccountPoint point) {
        assertEquals(0, new BigDecimal(expected).compareTo(point.growth()), "growth was " + point.growth());
    }

    @Test
    void theFirstDay_startsAtOne() {
        List<AccountPoint> points = historyOf(day(1, "1000", "1000"));

        assertGrowth("1", points.get(0));
    }

    @Test
    void theExampleOfTheNotes_minus1Percent_andNotMinus5_5() {
        // 1,000 € that go up 10 %; then 1,000 € more, and the 2,100 € go down 10 %
        List<AccountPoint> points = historyOf(
                day(1, "1000", "1000"),
                day(2, "1100", "1000"),
                day(3, "1890", "2000"));

        assertGrowth("1.1", points.get(1));
        assertGrowth("0.99", points.get(2));   // 1.10 x 0.90: the investments lost 1 %, whenever the money came in
    }

    @Test
    void aWithdrawal_isNotALoss() {
        // 500 € taken out; the 500 € left became 600 €: +20 %
        List<AccountPoint> points = historyOf(
                day(1, "1000", "1000"),
                day(2, "600", "500"));

        assertGrowth("1.2", points.get(1));
    }

    @Test
    void aDayWithoutSnapshot_isOneStepFromTheLastOne() {
        // No snapshot on the 2nd: from the 1st to the 3rd counts as one step
        List<AccountPoint> points = historyOf(
                day(1, "1000", "1000"),
                day(3, "1050", "1000"));

        assertEquals(2, points.size());
        assertGrowth("1.05", points.get(1));
    }

    @Test
    void anEmptyAccountTheDayBefore_doesNotDivideByZero_andKeepsTheGrowth() {
        List<AccountPoint> points = historyOf(
                day(1, "0", "0"),
                day(2, "0", "0"),
                day(3, "1000", "1000"));   // the first money: it starts here, not a gain

        assertGrowth("1", points.get(1));
        assertGrowth("1", points.get(2));
    }

    @Test
    void eachPoint_keepsItsDateValueAndDeposits_forTheTwoLinesOfTheChart() {
        AccountPoint point = historyOf(day(1, "1000.50", "900")).get(0);

        assertEquals(LocalDate.of(2026, 11, 1), point.date());
        assertEquals(new BigDecimal("1000.50"), point.value());
        assertEquals(new BigDecimal("900"), point.netDeposits());
    }

    @Test
    void withoutSnapshots_thereIsNoHistory() {
        assertEquals(List.of(), historyOf());
    }
}

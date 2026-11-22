package com.llijat.t212visualizer.service;

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

    @Mock
    private AccountService accountService;
    @Mock
    private TransactionService transactionService;
    @Mock
    private AccountSnapshotRepository repository;

    private AccountSnapshotService service;

    @BeforeEach
    void setUp() {
        service = new AccountSnapshotService(accountService, transactionService, repository);
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
        assertEquals(LocalDate.now(ZoneId.of("Europe/Madrid")), saved.getValue().getSnapshotDate());
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

    private static final LocalDate TODAY = LocalDate.now(ZoneId.of("Europe/Madrid"));

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
}

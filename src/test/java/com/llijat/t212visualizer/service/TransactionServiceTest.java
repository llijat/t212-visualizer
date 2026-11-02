package com.llijat.t212visualizer.service;

import com.llijat.t212visualizer.dto.T212TransactionDto;
import com.llijat.t212visualizer.dto.T212TransactionPageDto;
import com.llijat.t212visualizer.model.AccountTransaction;
import com.llijat.t212visualizer.repository.AccountTransactionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.HttpClientErrorException;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TransactionServiceTest {

    @Mock
    private T212Client t212Client;

    @Mock
    private AccountTransactionRepository transactionRepository;

    private TransactionService transactionService;

    @Captor
    private ArgumentCaptor<List<AccountTransaction>> savedCaptor;

    private static final String FIRST_PAGE = "/equity/history/transactions?limit=50";

    // Before each test: a new service, with the mocks and NO waiting (0 ms)
    @BeforeEach
    void setUp() {
        transactionService = new TransactionService(t212Client, transactionRepository, 0, 0);
    }

    private T212TransactionDto transaction(String reference) {
        return new T212TransactionDto(new BigDecimal("10.10"), "EUR", "2026-10-25T10:00:00Z", reference, "DEPOSIT");
    }

    private T212TransactionPageDto page(String nextPagePath, T212TransactionDto... items) {
        return new T212TransactionPageDto(List.of(items), nextPagePath);
    }

    // Builds the same error RestClient throws when T212 answers with that status (429, 401...)
    private HttpClientErrorException httpError(HttpStatus status) {
        return HttpClientErrorException.create(status, status.getReasonPhrase(), HttpHeaders.EMPTY, null, null);
    }

    @Test
    void sync_followsAllPages_andSavesEverything() {
        // Arrange: two invented pages; the first points to the second (with the /api/v0 prefix, like T212)
        when(t212Client.fetchTransactions(FIRST_PAGE))
                .thenReturn(page("/api/v0/equity/history/transactions?limit=50&cursor=b",
                        transaction("a"), transaction("b")));
        when(t212Client.fetchTransactions("/equity/history/transactions?limit=50&cursor=b"))
                .thenReturn(page(null, transaction("c")));

        // Act
        transactionService.syncTransactions();

        // Assert: saved ONCE, with all 3 transactions
        verify(transactionRepository).saveAll(savedCaptor.capture());
        assertEquals(3, savedCaptor.getValue().size());
    }

    @Test
    void sync_stopsAtFirstKnownTransaction() {
        // Arrange: a page with a known transaction in the middle, which DOES have a next page
        when(t212Client.fetchTransactions(FIRST_PAGE))
                .thenReturn(page("/api/v0/equity/history/transactions?limit=50&cursor=b",
                        transaction("new1"), transaction("known"), transaction("new2")));
        when(transactionRepository.existsById("new1")).thenReturn(false);
        when(transactionRepository.existsById("known")).thenReturn(true);

        // Act
        transactionService.syncTransactions();

        // Assert: only what comes before the known one is saved, and the next page is not requested
        verify(transactionRepository).saveAll(savedCaptor.capture());
        assertEquals(1, savedCaptor.getValue().size());
        assertEquals("new1", savedCaptor.getValue().get(0).getReference());
        verify(t212Client, times(1)).fetchTransactions(anyString());
    }

    @Test
    void sync_whenAPageFails_savesNothing() {
        // Arrange: the first page works, but the second fails (for example, the connection drops)
        when(t212Client.fetchTransactions(FIRST_PAGE))
                .thenReturn(page("/api/v0/equity/history/transactions?limit=50&cursor=b",
                        transaction("a"), transaction("b")));
        when(t212Client.fetchTransactions("/equity/history/transactions?limit=50&cursor=b"))
                .thenThrow(new RuntimeException("Connection lost"));

        // Act
        transactionService.syncTransactions();

        // Assert: all or nothing, the first page is not saved on its own
        verify(transactionRepository, never()).saveAll(any());
    }

    @Test
    void sync_retriesAfterTooManyRequests_andSavesEverything() {
        // Arrange: T212 answers 429 the first time; the second time the page arrives
        when(t212Client.fetchTransactions(FIRST_PAGE))
                .thenThrow(httpError(HttpStatus.TOO_MANY_REQUESTS))
                .thenReturn(page(null, transaction("a"), transaction("b")));

        // Act
        transactionService.syncTransactions();

        // Assert: the page was requested twice and everything was saved in the end
        verify(t212Client, times(2)).fetchTransactions(FIRST_PAGE);
        verify(transactionRepository).saveAll(savedCaptor.capture());
        assertEquals(2, savedCaptor.getValue().size());
    }

    @Test
    void sync_givesUpAfterThreeTooManyRequests_andSavesNothing() {
        // Arrange: T212 always answers 429
        when(t212Client.fetchTransactions(FIRST_PAGE))
                .thenThrow(httpError(HttpStatus.TOO_MANY_REQUESTS));

        // Act
        transactionService.syncTransactions();

        // Assert: exactly 3 attempts, then it gives up and saves nothing
        verify(t212Client, times(3)).fetchTransactions(FIRST_PAGE);
        verify(transactionRepository, never()).saveAll(any());
    }

    @Test
    void sync_doesNotRetryOtherErrors() {
        // Arrange: bad credentials (401); waiting would not fix them
        when(t212Client.fetchTransactions(FIRST_PAGE))
                .thenThrow(httpError(HttpStatus.UNAUTHORIZED));

        // Act
        transactionService.syncTransactions();

        // Assert: a single attempt, no retries, and nothing saved
        verify(t212Client, times(1)).fetchTransactions(FIRST_PAGE);
        verify(transactionRepository, never()).saveAll(any());
    }

    @Test
    void sync_savesTheExactAmount() {
        // Arrange: a transaction of 10.10 (see the transaction helper)
        when(t212Client.fetchTransactions(FIRST_PAGE))
                .thenReturn(page(null, transaction("a")));

        // Act
        transactionService.syncTransactions();

        // Assert: the amount reaches the database unchanged
        verify(transactionRepository).saveAll(savedCaptor.capture());
        assertEquals(new BigDecimal("10.10"), savedCaptor.getValue().get(0).getAmount());
    }

    @Test
    void firstSyncDone_isTrueAfterASuccessfulSync() {
        // Arrange
        when(t212Client.fetchTransactions(FIRST_PAGE))
                .thenReturn(page(null, transaction("a")));
        assertFalse(transactionService.isFirstSyncDone());

        // Act
        transactionService.syncTransactions();

        // Assert
        assertTrue(transactionService.isFirstSyncDone());
    }

    @Test
    void firstSyncDone_staysFalseWhenTheSyncFails() {
        // Arrange: T212 does not respond
        when(t212Client.fetchTransactions(FIRST_PAGE))
                .thenThrow(new RuntimeException("Connection lost"));

        // Act
        transactionService.syncTransactions();

        // Assert: the data may be stale, so it stays false
        assertFalse(transactionService.isFirstSyncDone());
    }

    @Test
    void getNetDeposits_usesCashbackThresholdOfFiveEuros() {
        // Arrange: the repository only answers if the threshold is 5 (compareTo treats 5.0 and 5.00 as equal)
        when(transactionRepository.sumNetDeposits(argThat(threshold -> threshold.compareTo(new BigDecimal("5")) == 0)))
                .thenReturn(new BigDecimal("9871.25"));

        // Act
        BigDecimal netDeposits = transactionService.getNetDeposits();

        // Assert
        assertEquals(new BigDecimal("9871.25"), netDeposits);
    }
}
package com.llijat.t212visualizer.service;

import com.llijat.t212visualizer.dto.CardExpenseImportResult;
import com.llijat.t212visualizer.model.CardCashback;
import com.llijat.t212visualizer.model.CardExpense;
import com.llijat.t212visualizer.repository.CardCashbackRepository;
import com.llijat.t212visualizer.repository.CardExpenseRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.io.StringReader;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CardExpenseImportServiceTest {

    private static final String HEADER = "Action,Time (UTC),ID,Total,Currency (Total),Merchant name,Merchant category\n";

    @Mock
    private CardExpenseRepository repository;

    @Mock
    private CardCashbackRepository cashbackRepository;

    @Captor
    private ArgumentCaptor<List<CardExpense>> savedCaptor;

    @Captor
    private ArgumentCaptor<List<CardCashback>> cashbackCaptor;

    private CardExpenseImportService importService;

    // The parser is the real one: it has no external dependencies and already has its own tests
    @BeforeEach
    void setUp() {
        importService = new CardExpenseImportService(new CardExpenseCsvParser(), repository, cashbackRepository);
    }

    private String cardDebit(String id) {
        return "Card debit,2026-01-02 10:00:00+00:00," + id + ",-5.00,EUR,Test Market,RETAIL_STORES\n";
    }

    // Cashback rows only fill these columns; it arrives at night, after the day of the payment
    private String cashback(String id) {
        return "Spending cashback,2026-01-03 01:12:00+00:00," + id + ",0.08,EUR,,\n";
    }

    @Test
    void importCsv_savesTheNewExpenses() throws IOException {
        // Arrange
        String csv = HEADER + cardDebit("card-1") + cardDebit("card-2");
        when(repository.existsById("card-1")).thenReturn(false);
        when(repository.existsById("card-2")).thenReturn(false);

        // Act
        CardExpenseImportResult result = importService.importCsv(new StringReader(csv));

        // Assert
        verify(repository).saveAll(savedCaptor.capture());
        assertEquals(2, savedCaptor.getValue().size());
        assertEquals(2, result.imported());
        assertEquals(0, result.alreadyKnown());
    }

    @Test
    void importCsv_skipsTheExpensesItAlreadyHas() throws IOException {
        // Arrange: uploading overlapping files must not count a payment twice
        String csv = HEADER + cardDebit("known") + cardDebit("new");
        when(repository.existsById("known")).thenReturn(true);
        when(repository.existsById("new")).thenReturn(false);

        // Act
        CardExpenseImportResult result = importService.importCsv(new StringReader(csv));

        // Assert: only the new one is saved
        verify(repository).saveAll(savedCaptor.capture());
        assertEquals(1, savedCaptor.getValue().size());
        assertEquals("new", savedCaptor.getValue().get(0).getReference());
        assertEquals(1, result.imported());
        assertEquals(1, result.alreadyKnown());
    }

    @Test
    void importCsv_reportsTheIgnoredActions() throws IOException {
        // Arrange
        String csv = HEADER + "Market buy,2026-01-02 10:00:00+00:00,order-1,100.00,EUR,,\n";

        // Act
        CardExpenseImportResult result = importService.importCsv(new StringReader(csv));

        // Assert
        assertEquals(0, result.imported());
        assertEquals(Map.of("Market buy", 1L), result.ignoredByAction());
    }

    @Test
    void importCsv_whenTheFileIsInvalid_savesNothing() {
        // Arrange: the first row is fine, but the second has an invalid amount
        String csv = HEADER + cardDebit("card-1")
                + "Card debit,2026-01-02 10:00:00+00:00,card-2,not-a-number,EUR,Test Market,RETAIL_STORES\n";

        // Act and Assert: all or nothing, the error reaches whoever called the service
        assertThrows(IllegalArgumentException.class, () -> importService.importCsv(new StringReader(csv)));
        verify(repository, never()).saveAll(any());
        verify(cashbackRepository, never()).saveAll(any());
    }

    // ===== Cashback =====

    @Test
    void importCsv_savesTheCashbackApart_fromThePayments() throws IOException {
        String csv = HEADER + cardDebit("card-1") + cashback("cb-1") + cashback("cb-2");

        CardExpenseImportResult result = importService.importCsv(new StringReader(csv));

        verify(repository).saveAll(savedCaptor.capture());
        assertEquals(List.of("card-1"), savedCaptor.getValue().stream().map(CardExpense::getReference).toList());
        verify(cashbackRepository).saveAll(cashbackCaptor.capture());
        CardCashback first = cashbackCaptor.getValue().get(0);
        assertEquals("cb-1", first.getReference());
        assertEquals(Instant.parse("2026-01-03T01:12:00Z"), first.getDateTime());
        assertEquals(new BigDecimal("0.08"), first.getAmount());
        assertEquals("EUR", first.getCurrency());
        assertEquals(1, result.imported());
        assertEquals(2, result.cashbackImported());
        assertEquals(Map.of(), result.ignoredByAction());   // cashback is no longer skipped
    }

    @Test
    void importCsv_skipsTheCashbackItAlreadyHas() throws IOException {
        // Two exports that overlap on one day share that day's cashback
        String csv = HEADER + cashback("known") + cashback("new");
        when(cashbackRepository.existsById("known")).thenReturn(true);
        when(cashbackRepository.existsById("new")).thenReturn(false);

        CardExpenseImportResult result = importService.importCsv(new StringReader(csv));

        verify(cashbackRepository).saveAll(cashbackCaptor.capture());
        assertEquals(List.of("new"), cashbackCaptor.getValue().stream().map(CardCashback::getReference).toList());
        assertEquals(1, result.cashbackImported());
    }

    @Test
    void importCsv_savesAllTheCashbackInOneGo() throws IOException {
        // Saving inside the loop would give the right rows, but call the database once per row
        String csv = HEADER + cashback("cb-1") + cashback("cb-2") + cashback("cb-3");

        importService.importCsv(new StringReader(csv));

        verify(cashbackRepository, times(1)).saveAll(any());
    }
}

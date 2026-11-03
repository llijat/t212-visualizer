package com.llijat.t212visualizer.service;

import com.llijat.t212visualizer.dto.CardExpenseImportResult;
import com.llijat.t212visualizer.model.CardExpense;
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
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CardExpenseImportServiceTest {

    private static final String HEADER = "Action,Time (UTC),ID,Total,Currency (Total),Merchant name,Merchant category\n";

    @Mock
    private CardExpenseRepository repository;

    @Captor
    private ArgumentCaptor<List<CardExpense>> savedCaptor;

    private CardExpenseImportService importService;

    // The parser is the real one: it has no external dependencies and already has its own tests
    @BeforeEach
    void setUp() {
        importService = new CardExpenseImportService(new CardExpenseCsvParser(), repository);
    }

    private String cardDebit(String id) {
        return "Card debit,2026-01-02 10:00:00+00:00," + id + ",-5.00,EUR,Test Market,RETAIL_STORES\n";
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
    }
}

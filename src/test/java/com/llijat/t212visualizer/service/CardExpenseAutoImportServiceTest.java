package com.llijat.t212visualizer.service;

import com.llijat.t212visualizer.dto.CardExpenseImportResult;
import com.llijat.t212visualizer.dto.T212ExportReportDto;
import com.llijat.t212visualizer.model.CardExpense;
import com.llijat.t212visualizer.repository.CardExpenseRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.Reader;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CardExpenseAutoImportServiceTest {

    private static final long REPORT_ID = 7L;
    private static final String LINK = "https://downloads.example.test/report.csv";

    @Mock
    private T212ExportClient exportClient;

    @Mock
    private CardExpenseImportService importService;

    @Mock
    private CardExpenseRepository repository;

    @Captor
    private ArgumentCaptor<Instant> fromCaptor;

    @Captor
    private ArgumentCaptor<Instant> toCaptor;

    @Captor
    private ArgumentCaptor<Reader> readerCaptor;

    private CardExpenseAutoImportService autoImportService;

    // Before each test: a new service, with the mocks and NO waiting (0 ms)
    @BeforeEach
    void setUp() {
        autoImportService = new CardExpenseAutoImportService(exportClient, importService, repository, 0);
    }

    private CardExpense paymentOn(String dateTime) {
        return new CardExpense("known", "Card debit", Instant.parse(dateTime), new BigDecimal("-5.00"), "EUR", "Market", "RETAIL_STORES");
    }

    private T212ExportReportDto report(long id, String status, String link) {
        return new T212ExportReportDto(id, status, link);
    }

    private void aReportIsRequested() {
        when(repository.findTopByOrderByDateTimeDesc()).thenReturn(Optional.empty());
        when(exportClient.requestExport(any(), any())).thenReturn(REPORT_ID);
    }

    @Test
    void importLatest_asksForTheDaysSinceTheLastPayment_withAWeekOfMargin() throws IOException {
        // Arrange: a payment can settle days after it was made, with its original date, so a week is repeated
        when(repository.findTopByOrderByDateTimeDesc()).thenReturn(Optional.of(paymentOn("2026-10-20T10:00:00Z")));
        when(exportClient.requestExport(any(), any())).thenReturn(REPORT_ID);
        when(exportClient.fetchExports()).thenReturn(List.of(report(REPORT_ID, "Failed", null)));

        // Act
        Instant before = Instant.now();
        autoImportService.importLatest();

        // Assert
        verify(exportClient).requestExport(fromCaptor.capture(), toCaptor.capture());
        assertEquals(Instant.parse("2026-10-13T10:00:00Z"), fromCaptor.getValue());
        assertTrue(!toCaptor.getValue().isBefore(before));
    }

    @Test
    void importLatest_withoutPayments_asksForTheLastYear() throws IOException {
        // Arrange
        aReportIsRequested();
        when(exportClient.fetchExports()).thenReturn(List.of(report(REPORT_ID, "Failed", null)));

        // Act
        autoImportService.importLatest();

        // Assert
        verify(exportClient).requestExport(fromCaptor.capture(), toCaptor.capture());
        assertEquals(Duration.ofDays(365), Duration.between(fromCaptor.getValue(), toCaptor.getValue()));
    }

    @Test
    void importLatest_neverAsksForMoreThanAYear() throws IOException {
        // Arrange: the PC was off for a long time
        when(repository.findTopByOrderByDateTimeDesc()).thenReturn(Optional.of(paymentOn("2020-01-01T10:00:00Z")));
        when(exportClient.requestExport(any(), any())).thenReturn(REPORT_ID);
        when(exportClient.fetchExports()).thenReturn(List.of(report(REPORT_ID, "Failed", null)));

        // Act
        autoImportService.importLatest();

        // Assert
        verify(exportClient).requestExport(fromCaptor.capture(), toCaptor.capture());
        assertEquals(Duration.ofDays(365), Duration.between(fromCaptor.getValue(), toCaptor.getValue()));
    }

    @Test
    void importLatest_waitsUntilTheReportIsFinished_andImportsIt() throws IOException {
        // Arrange: T212 takes a while to generate the report
        aReportIsRequested();
        when(exportClient.fetchExports())
                .thenReturn(List.of(report(REPORT_ID, "Queued", null)))
                .thenReturn(List.of(report(REPORT_ID, "Running", null)))
                .thenReturn(List.of(report(REPORT_ID, "Finished", LINK)));
        when(exportClient.downloadExport(LINK)).thenReturn("the csv");
        when(importService.importCsv(any())).thenReturn(new CardExpenseImportResult(1, 0, Map.of()));

        // Act
        autoImportService.importLatest();

        // Assert: the importer receives exactly what was downloaded
        verify(exportClient, times(3)).fetchExports();
        verify(importService).importCsv(readerCaptor.capture());
        assertEquals("the csv", new BufferedReader(readerCaptor.getValue()).readLine());
    }

    @Test
    void importLatest_usesItsOwnReport_notAnotherFinishedOne() throws IOException {
        // Arrange: the list also contains older reports
        aReportIsRequested();
        when(exportClient.fetchExports()).thenReturn(List.of(
                report(1L, "Finished", "https://downloads.example.test/old.csv"),
                report(REPORT_ID, "Finished", LINK)));
        when(exportClient.downloadExport(LINK)).thenReturn("the csv");
        when(importService.importCsv(any())).thenReturn(new CardExpenseImportResult(0, 0, Map.of()));

        // Act
        autoImportService.importLatest();

        // Assert
        verify(exportClient).downloadExport(LINK);
    }

    @Test
    void importLatest_whenTheReportFails_downloadsNothing() throws IOException {
        // Arrange
        aReportIsRequested();
        when(exportClient.fetchExports()).thenReturn(List.of(report(REPORT_ID, "Failed", null)));

        // Act
        autoImportService.importLatest();

        // Assert: asking again would not help, so it stops at the first answer
        verify(exportClient, times(1)).fetchExports();
        verify(exportClient, never()).downloadExport(anyString());
        verify(importService, never()).importCsv(any());
    }

    @Test
    void importLatest_givesUpAfterTenChecks() throws IOException {
        // Arrange: the report never finishes
        aReportIsRequested();
        when(exportClient.fetchExports()).thenReturn(List.of(report(REPORT_ID, "Processing", null)));

        // Act
        autoImportService.importLatest();

        // Assert
        verify(exportClient, times(10)).fetchExports();
        verify(importService, never()).importCsv(any());
    }

    @Test
    void importLatest_whenTheApiFails_doesNotBreakTheScheduledTask() throws IOException {
        // Arrange: for example, the key has no permission or there is no connection
        when(repository.findTopByOrderByDateTimeDesc()).thenReturn(Optional.empty());
        when(exportClient.requestExport(any(), any())).thenThrow(new RuntimeException("Connection lost"));

        // Act: if the exception escaped, the test would fail here
        autoImportService.importLatest();

        // Assert
        verify(importService, never()).importCsv(any());
    }
}

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

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
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

    // Antes de cada test: un service nuevo, con los mocks y SIN esperas (0 ms)
    @BeforeEach
    void setUp() {
        transactionService = new TransactionService(t212Client, transactionRepository, 0, 0);
    }

    private T212TransactionDto transaction(String reference) {
        return new T212TransactionDto(10.0, "EUR", "2026-10-25T10:00:00Z", reference, "DEPOSIT");
    }

    private T212TransactionPageDto page(String nextPagePath, T212TransactionDto... items) {
        return new T212TransactionPageDto(List.of(items), nextPagePath);
    }

    // Crea el mismo error que lanza RestClient cuando T212 responde con ese código (429, 401...)
    private HttpClientErrorException httpError(HttpStatus status) {
        return HttpClientErrorException.create(status, status.getReasonPhrase(), HttpHeaders.EMPTY, null, null);
    }

    @Test
    void sync_followsAllPages_andSavesEverything() {
        // Preparar: dos páginas inventadas; la primera apunta a la segunda (con el prefijo /api/v0, como T212)
        when(t212Client.fetchTransactions(FIRST_PAGE))
                .thenReturn(page("/api/v0/equity/history/transactions?limit=50&cursor=b",
                        transaction("a"), transaction("b")));
        when(t212Client.fetchTransactions("/equity/history/transactions?limit=50&cursor=b"))
                .thenReturn(page(null, transaction("c")));

        // Ejecutar
        transactionService.syncTransactions();

        // Comprobar: se ha guardado UNA vez, con los 3 movimientos
        verify(transactionRepository).saveAll(savedCaptor.capture());
        assertEquals(3, savedCaptor.getValue().size());
    }

    @Test
    void sync_stopsAtFirstKnownTransaction() {
        // Preparar: una página con un movimiento conocido en medio, y que SÍ tiene página siguiente
        when(t212Client.fetchTransactions(FIRST_PAGE))
                .thenReturn(page("/api/v0/equity/history/transactions?limit=50&cursor=b",
                        transaction("nuevo1"), transaction("conocido"), transaction("nuevo2")));
        when(transactionRepository.existsById("nuevo1")).thenReturn(false);
        when(transactionRepository.existsById("conocido")).thenReturn(true);

        // Ejecutar
        transactionService.syncTransactions();

        // Comprobar: solo se guarda lo anterior al conocido, y no se pide la página siguiente
        verify(transactionRepository).saveAll(savedCaptor.capture());
        assertEquals(1, savedCaptor.getValue().size());
        assertEquals("nuevo1", savedCaptor.getValue().get(0).getReference());
        verify(t212Client, times(1)).fetchTransactions(anyString());
    }

    @Test
    void sync_whenAPageFails_savesNothing() {
        // Preparar: la primera página va bien, pero la segunda falla (por ejemplo, se corta la conexión)
        when(t212Client.fetchTransactions(FIRST_PAGE))
                .thenReturn(page("/api/v0/equity/history/transactions?limit=50&cursor=b",
                        transaction("a"), transaction("b")));
        when(t212Client.fetchTransactions("/equity/history/transactions?limit=50&cursor=b"))
                .thenThrow(new RuntimeException("Se ha cortado la conexión"));

        // Ejecutar
        transactionService.syncTransactions();

        // Comprobar: todo o nada, no se guarda la primera página a medias
        verify(transactionRepository, never()).saveAll(any());
    }

    @Test
    void sync_retriesAfterTooManyRequests_andSavesEverything() {
        // Preparar: la primera vez T212 responde 429; la segunda, la página llega bien
        when(t212Client.fetchTransactions(FIRST_PAGE))
                .thenThrow(httpError(HttpStatus.TOO_MANY_REQUESTS))
                .thenReturn(page(null, transaction("a"), transaction("b")));

        // Ejecutar
        transactionService.syncTransactions();

        // Comprobar: ha pedido la página 2 veces y, al final, ha guardado todo
        verify(t212Client, times(2)).fetchTransactions(FIRST_PAGE);
        verify(transactionRepository).saveAll(savedCaptor.capture());
        assertEquals(2, savedCaptor.getValue().size());
    }

    @Test
    void sync_givesUpAfterThreeTooManyRequests_andSavesNothing() {
        // Preparar: T212 responde 429 siempre
        when(t212Client.fetchTransactions(FIRST_PAGE))
                .thenThrow(httpError(HttpStatus.TOO_MANY_REQUESTS));

        // Ejecutar
        transactionService.syncTransactions();

        // Comprobar: lo intenta exactamente 3 veces, se rinde y no guarda nada
        verify(t212Client, times(3)).fetchTransactions(FIRST_PAGE);
        verify(transactionRepository, never()).saveAll(any());
    }

    @Test
    void sync_doesNotRetryOtherErrors() {
        // Preparar: credenciales malas (401); esperar no lo arreglaría
        when(t212Client.fetchTransactions(FIRST_PAGE))
                .thenThrow(httpError(HttpStatus.UNAUTHORIZED));

        // Ejecutar
        transactionService.syncTransactions();

        // Comprobar: un solo intento, sin reintentos, y nada guardado
        verify(t212Client, times(1)).fetchTransactions(FIRST_PAGE);
        verify(transactionRepository, never()).saveAll(any());
    }

    @Test
    void getNetDeposits_usesCashbackThresholdOfFiveEuros() {
        // Preparar: el repositorio solo responde si le piden el umbral de 5 €
        when(transactionRepository.sumNetDeposits(5.0)).thenReturn(9871.25);

        // Ejecutar
        double netDeposits = transactionService.getNetDeposits();

        // Comprobar
        assertEquals(9871.25, netDeposits, 0.001);
    }
}
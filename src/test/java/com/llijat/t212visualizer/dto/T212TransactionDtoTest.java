package com.llijat.t212visualizer.dto;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;

class T212TransactionDtoTest {

    @Test
    void amount_isReadFromJsonWithoutLosingDigits() throws Exception {
        // Preparar: un importe con más dígitos de los que un double puede guardar
        String json = """
                {"amount": 1234567.1234567891, "currency": "EUR",
                 "dateTime": "2026-10-25T10:00:00Z", "reference": "a", "type": "DEPOSIT"}
                """;

        // Ejecutar
        T212TransactionDto dto = new ObjectMapper().readValue(json, T212TransactionDto.class);

        // Comprobar: llega exacto, sin pasar por double
        assertEquals(new BigDecimal("1234567.1234567891"), dto.amount());
    }
}

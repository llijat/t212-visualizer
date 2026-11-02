package com.llijat.t212visualizer.dto;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;

class T212TransactionDtoTest {

    @Test
    void amount_isReadFromJsonWithoutLosingDigits() throws Exception {
        // Arrange: an amount with more digits than a double can hold
        String json = """
                {"amount": 1234567.1234567891, "currency": "EUR",
                 "dateTime": "2026-10-25T10:00:00Z", "reference": "a", "type": "DEPOSIT"}
                """;

        // Act
        T212TransactionDto dto = new ObjectMapper().readValue(json, T212TransactionDto.class);

        // Assert: it arrives exact, without going through double
        assertEquals(new BigDecimal("1234567.1234567891"), dto.amount());
    }
}

package com.llijat.t212visualizer.service;

import com.llijat.t212visualizer.dto.CardExpenseCsvResult;
import com.llijat.t212visualizer.model.CardExpense;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.StringReader;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CardExpenseCsvParserTest {

    private static final String HEADER = "Action,Time (UTC),ID,Total,Currency (Total),Merchant name,Merchant category\n";

    private final CardExpenseCsvParser parser = new CardExpenseCsvParser();

    private CardExpenseCsvResult parse(String csv) throws IOException {
        return parser.parse(new StringReader(csv));
    }

    @Test
    void parse_readsAllTheFieldsOfACardDebit() throws IOException {
        // Arrange
        String csv = HEADER + "Card debit,2025-09-11 05:30:26+00:00,card-1,-12.34,EUR,Test Market,RETAIL_STORES\n";

        // Act
        CardExpenseCsvResult result = parse(csv);

        // Assert
        assertEquals(1, result.expenses().size());
        CardExpense expense = result.expenses().get(0);
        assertEquals("card-1", expense.getReference());
        assertEquals("Card debit", expense.getAction());
        assertEquals(Instant.parse("2025-09-11T05:30:26Z"), expense.getDateTime());
        assertEquals(new BigDecimal("-12.34"), expense.getAmount());
        assertEquals("EUR", expense.getCurrency());
        assertEquals("Test Market", expense.getMerchantName());
        assertEquals("RETAIL_STORES", expense.getMerchantCategory());
    }

    @Test
    void parse_findsTheColumnsByName_whateverTheirOrder() throws IOException {
        // Arrange: other order and extra columns, as happens between real T212 exports
        String csv = """
                Merchant category,Total,Deposit fee,ID,Currency (Total),Time (UTC),Merchant name,Action
                RESTAURANTS,-8.50,,card-1,EUR,2026-01-02 10:00:00+00:00,Test Bar,Card debit
                """;

        // Act
        CardExpense expense = parse(csv).expenses().get(0);

        // Assert
        assertEquals("card-1", expense.getReference());
        assertEquals(new BigDecimal("-8.50"), expense.getAmount());
        assertEquals("Test Bar", expense.getMerchantName());
        assertEquals("RESTAURANTS", expense.getMerchantCategory());
    }

    @Test
    void parse_acceptsCommasAndQuotesInsideTheMerchantName() throws IOException {
        // Arrange: in CSV, a value with commas goes in quotes, and a quote inside it is written twice
        String csv = HEADER + "Card debit,2026-01-02 10:00:00+00:00,card-1,-5.00,EUR,\"Bar, Grill \"\"Test\"\"\",RESTAURANTS\n";

        // Act
        CardExpense expense = parse(csv).expenses().get(0);

        // Assert
        assertEquals("Bar, Grill \"Test\"", expense.getMerchantName());
    }

    @Test
    void parse_ignoresOtherActions_andCountsThem() throws IOException {
        // Arrange: only one card debit. The dividend has no ID, like the real ones, and must not cause an error
        String csv = HEADER
                + "Market buy,2026-01-02 10:00:00+00:00,order-1,100.00,EUR,,\n"
                + "Card debit,2026-01-02 11:00:00+00:00,card-1,-5.00,EUR,Test Market,RETAIL_STORES\n"
                + "Market buy,2026-01-02 12:00:00+00:00,order-2,50.00,EUR,,\n"
                + "Dividend (Dividend),2026-01-03 10:00:00+00:00,,0.05,EUR,,\n";

        // Act
        CardExpenseCsvResult result = parse(csv);

        // Assert
        assertEquals(1, result.expenses().size());
        assertEquals("card-1", result.expenses().get(0).getReference());
        assertEquals(Map.of("Market buy", 2L, "Dividend (Dividend)", 1L), result.ignoredByAction());
    }

    @Test
    void parse_withOnlyTheHeader_returnsNothing() throws IOException {
        // Act
        CardExpenseCsvResult result = parse(HEADER);

        // Assert
        assertTrue(result.expenses().isEmpty());
        assertTrue(result.ignoredByAction().isEmpty());
    }

    @Test
    void parse_whenARequiredColumnIsMissing_fails() {
        // Arrange: this file has no "Total" column
        String csv = """
                Action,Time (UTC),ID,Currency (Total),Merchant name,Merchant category
                Card debit,2026-01-02 10:00:00+00:00,card-1,EUR,Test Market,RETAIL_STORES
                """;

        // Act and Assert: better to reject the file than to save incomplete expenses
        assertThrows(IllegalArgumentException.class, () -> parse(csv));
    }

    @Test
    void parse_whenAnAmountIsNotANumber_fails() {
        // Arrange
        String csv = HEADER + "Card debit,2026-01-02 10:00:00+00:00,card-1,not-a-number,EUR,Test Market,RETAIL_STORES\n";

        // Act and Assert
        assertThrows(IllegalArgumentException.class, () -> parse(csv));
    }
}

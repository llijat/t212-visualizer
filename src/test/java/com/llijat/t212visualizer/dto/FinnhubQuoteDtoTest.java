package com.llijat.t212visualizer.dto;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FinnhubQuoteDtoTest {

    // Like the one Spring uses for the RestClient: the fields the record does not have (d, h, l, o, pc) are skipped
    private final ObjectMapper json = new ObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    @Test
    void theShortNamesOfTheJson_goToReadableNames() throws Exception {
        // One answer as Finnhub's /quote sends it (field names copied from its documentation)
        FinnhubQuoteDto quote = json.readValue("""
                {"c": 182.5, "d": -7.98, "dp": -4.19, "h": 190.1, "l": 181.9, "o": 189.7, "pc": 190.48, "t": 1794862800}
                """, FinnhubQuoteDto.class);

        assertEquals(new BigDecimal("182.5"), quote.price());
        assertEquals(new BigDecimal("-4.19"), quote.changePercent());
        assertEquals(1794862800L, quote.time());
    }

    @Test
    void anUnknownSymbol_comesWithZerosAndNulls() throws Exception {
        // What Finnhub answers for a symbol it does not know
        FinnhubQuoteDto quote = json.readValue("""
                {"c": 0, "d": null, "dp": null, "h": 0, "l": 0, "o": 0, "pc": 0, "t": 0}
                """, FinnhubQuoteDto.class);

        assertEquals(BigDecimal.ZERO, quote.price());
        assertNull(quote.changePercent());
        assertEquals(0L, quote.time());
    }

    @Test
    void aNullTime_becomesZero_becauseALongCannotBeNull() throws Exception {
        FinnhubQuoteDto quote = json.readValue("""
                {"c": 0, "dp": null, "t": null}
                """, FinnhubQuoteDto.class);

        assertEquals(0L, quote.time());
    }

    // ===== Is it a real quote? =====

    @Test
    void aRealQuote_isKnown() {
        assertTrue(new FinnhubQuoteDto(new BigDecimal("182.5"), new BigDecimal("-4.19"), 1794862800L).isKnown());
    }

    @Test
    void aQuoteThatDidNotMove_isStillKnown() {
        // 0 % is a real answer: the price did not change. Only null means "no answer"
        assertTrue(new FinnhubQuoteDto(new BigDecimal("182.5"), BigDecimal.ZERO, 1794862800L).isKnown());
    }

    @Test
    void withoutAPercentage_itIsNotKnown() {
        assertFalse(new FinnhubQuoteDto(BigDecimal.ZERO, null, 0L).isKnown());
    }

    @Test
    void withoutAMoment_itIsNotKnown() {
        assertFalse(new FinnhubQuoteDto(new BigDecimal("182.5"), new BigDecimal("-4.19"), 0L).isKnown());
    }
}

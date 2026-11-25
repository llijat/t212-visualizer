package com.llijat.t212visualizer.service;

import com.llijat.t212visualizer.dto.AlphaVantageDividendDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/** MockRestServiceServer pretends to be Alpha Vantage: no real request leaves the test. */
class LiveAlphaVantageClientTest {

    private static final String BASE_URL = "https://alphavantage.example.test";

    private MockRestServiceServer server;
    private LiveAlphaVantageClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder().baseUrl(BASE_URL);
        server = MockRestServiceServer.bindTo(builder).build();
        client = new LiveAlphaVantageClient(builder.build());
    }

    @Test
    void fetchDividends_asksForOneSymbol_andReadsEveryDividend() {
        // The real shape, taken from Alpha Vantage's example (IBM): every value is text, and two dates are not used
        String json = """
                {"symbol": "MU", "data": [
                  {"ex_dividend_date": "2026-12-29", "declaration_date": "2026-12-18", "record_date": "2026-12-29",
                   "payment_date": "2027-01-14", "amount": "0.115"},
                  {"ex_dividend_date": "2026-09-30", "declaration_date": "2026-09-23", "record_date": "2026-09-30",
                   "payment_date": "2026-10-16", "amount": "0.115"}
                ]}
                """;
        server.expect(requestTo(BASE_URL + "/query?function=DIVIDENDS&symbol=MU"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(json, MediaType.APPLICATION_JSON));

        List<AlphaVantageDividendDto> dividends = client.fetchDividends("MU");

        server.verify();
        assertEquals(2, dividends.size());
        assertEquals(LocalDate.of(2027, 1, 14), dividends.get(0).paymentDay());
        assertEquals(LocalDate.of(2026, 12, 29), dividends.get(0).exDividendDay());
        assertEquals(new BigDecimal("0.115"), dividends.get(0).amountPerShare());
    }

    @Test
    void aStockThatNeverPays_isAnEmptyList() {
        server.expect(requestTo(BASE_URL + "/query?function=DIVIDENDS&symbol=AMZN"))
                .andRespond(withSuccess("{\"symbol\": \"AMZN\", \"data\": []}", MediaType.APPLICATION_JSON));

        assertTrue(client.fetchDividends("AMZN").isEmpty());
    }

    @Test
    void overTheDailyLimit_itFails_insteadOfSayingThereAreNoDividends() {
        // Alpha Vantage answers 200 with a notice and no "data": an empty list would mark the stock as checked
        server.expect(requestTo(BASE_URL + "/query?function=DIVIDENDS&symbol=MU"))
                .andRespond(withSuccess("{\"Information\": \"Our standard API rate limit is 25 requests per day.\"}", MediaType.APPLICATION_JSON));

        IllegalStateException error = assertThrows(IllegalStateException.class, () -> client.fetchDividends("MU"));
        assertTrue(error.getMessage().contains("MU"));
    }

    // ===== Daily closes =====

    @Test
    void fetchDailyCloses_readsTheCloseOfEachDay() {
        // The real shape: one object per day, keyed by the date, with the numbers as text
        String json = """
                {"Meta Data": {"2. Symbol": "MU"},
                 "Time Series (Daily)": {
                   "2026-11-13": {"1. open": "101.00", "2. high": "104.10", "3. low": "100.50", "4. close": "103.2500", "5. volume": "1200"},
                   "2026-11-12": {"1. open": "99.00", "2. high": "101.30", "3. low": "98.70", "4. close": "100.9000", "5. volume": "900"}
                 }}
                """;
        server.expect(requestTo(BASE_URL + "/query?function=TIME_SERIES_DAILY&symbol=MU&outputsize=compact"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(json, MediaType.APPLICATION_JSON));

        Map<LocalDate, BigDecimal> closes = client.fetchDailyCloses("MU");

        server.verify();
        assertEquals(2, closes.size());
        assertEquals(new BigDecimal("103.2500"), closes.get(LocalDate.of(2026, 11, 13)));   // the close, not the open or the high
        assertEquals(new BigDecimal("100.9000"), closes.get(LocalDate.of(2026, 11, 12)));
    }

    @Test
    void overTheDailyLimit_theClosesFail_insteadOfBeingEmpty() {
        // An empty answer would look like a stock with no history; failing lets the next day try again
        server.expect(requestTo(BASE_URL + "/query?function=TIME_SERIES_DAILY&symbol=MU&outputsize=compact"))
                .andRespond(withSuccess("{\"Information\": \"Our standard API rate limit is 25 requests per day.\"}", MediaType.APPLICATION_JSON));

        IllegalStateException error = assertThrows(IllegalStateException.class, () -> client.fetchDailyCloses("MU"));
        assertTrue(error.getMessage().contains("MU"));
    }
}

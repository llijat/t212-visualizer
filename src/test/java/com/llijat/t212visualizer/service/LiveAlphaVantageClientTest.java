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
}

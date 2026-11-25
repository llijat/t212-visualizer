package com.llijat.t212visualizer.service;

import com.llijat.t212visualizer.dto.T212DividendDto;
import com.llijat.t212visualizer.dto.T212DividendPageDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/** MockRestServiceServer pretends to be Trading 212: no real request leaves the test. */
class LiveT212ClientTest {

    private static final String BASE_URL = "https://t212.example.test/api/v0";

    private MockRestServiceServer server;
    private LiveT212Client client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder().baseUrl(BASE_URL);
        server = MockRestServiceServer.bindTo(builder).build();
        client = new LiveT212Client(builder.build());
    }

    @Test
    void fetchDividends_readsThePage_withTheShapeTheRealApiSends() {
        // The fields and types that the real endpoint returned on 22/11/2026, with invented values.
        // "instrument" is nested and several fields are not used: none of that may break the reading.
        String json = """
                {"items": [
                  {"ticker": "MU_US_EQ",
                   "instrument": {"ticker": "MU_US_EQ", "name": "Micron Technology", "isin": "US5951121038", "currency": "USD"},
                   "reference": "a1b2c3", "quantity": 8.5, "amount": 0.84, "currency": "EUR",
                   "grossAmountPerShare": 0.115, "amountInEuro": 0.84,
                   "paidOn": "2026-10-16T09:30:12.000+02:00", "type": "ORDINARY"},
                  {"ticker": "MU_US_EQ", "reference": "d4e5f6", "quantity": 8.5, "amount": 0.84, "currency": "EUR",
                   "grossAmountPerShare": 0.115, "amountInEuro": 0.84,
                   "paidOn": "2026-07-17T09:31:40.000+02:00", "type": "ORDINARY"}
                ],
                "nextPagePath": "/api/v0/equity/history/dividends?limit=50&cursor=1752737500000"}
                """;
        server.expect(requestTo(BASE_URL + "/equity/history/dividends?limit=50"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(json, MediaType.APPLICATION_JSON));

        T212DividendPageDto page = client.fetchDividends("/equity/history/dividends?limit=50");

        server.verify();
        assertEquals(2, page.items().size());
        T212DividendDto newest = page.items().get(0);
        assertEquals("MU_US_EQ", newest.ticker());
        assertEquals(new BigDecimal("8.5"), newest.quantity());
        assertEquals(new BigDecimal("0.115"), newest.grossAmountPerShare());
        assertEquals(new BigDecimal("0.84"), newest.amountInEuro());
        assertEquals(LocalDate.of(2026, 10, 16), newest.paidDay());
        assertEquals("/api/v0/equity/history/dividends?limit=50&cursor=1752737500000", page.nextPagePath());
    }

    @Test
    void theLastPage_hasNoNextPath() {
        server.expect(requestTo(BASE_URL + "/equity/history/dividends?limit=50"))
                .andRespond(withSuccess("{\"items\": [], \"nextPagePath\": null}", MediaType.APPLICATION_JSON));

        assertNull(client.fetchDividends("/equity/history/dividends?limit=50").nextPagePath());
    }
}

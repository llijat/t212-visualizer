package com.llijat.t212visualizer.service;

import com.llijat.t212visualizer.dto.FinnhubEarningsDto;
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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/** MockRestServiceServer pretends to be Finnhub: no real request leaves the test. */
class LiveFinnhubClientTest {

    private static final String BASE_URL = "https://finnhub.example.test/api/v1";

    private MockRestServiceServer server;
    private LiveFinnhubClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder().baseUrl(BASE_URL);
        server = MockRestServiceServer.bindTo(builder).build();
        client = new LiveFinnhubClient(builder.build());
    }

    @Test
    void fetchEarnings_asksForOneSymbolInTheGivenPeriod_andReadsEveryField() {
        // Arrange: one published result, one upcoming result whose figures are still unknown,
        // and a field Finnhub might add one day, which must not break anything
        String json = """
                {"earningsCalendar": [
                  {"symbol": "MU", "date": "2026-09-23", "hour": "amc", "quarter": 4, "year": 2026,
                   "epsEstimate": 1.65, "epsActual": 1.79, "revenueEstimate": 8700000000, "revenueActual": 8900000000,
                   "someNewField": "ignored"},
                  {"symbol": "MU", "date": "2026-12-17", "hour": "", "quarter": 1, "year": 2027,
                   "epsEstimate": null, "epsActual": null, "revenueEstimate": null, "revenueActual": null}
                ]}
                """;
        server.expect(requestTo(BASE_URL + "/calendar/earnings?symbol=MU&from=2026-09-01&to=2026-12-31"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(json, MediaType.APPLICATION_JSON));

        // Act
        List<FinnhubEarningsDto> earnings = client.fetchEarnings("MU", LocalDate.of(2026, 9, 1), LocalDate.of(2026, 12, 31));

        // Assert
        server.verify();
        assertEquals(2, earnings.size());

        FinnhubEarningsDto published = earnings.get(0);
        assertEquals("MU", published.symbol());
        assertEquals(LocalDate.of(2026, 9, 23), published.date());
        assertEquals("amc", published.hour());
        assertEquals(4, published.quarter());
        assertEquals(2026, published.year());
        assertEquals(new BigDecimal("1.65"), published.epsEstimate());
        assertEquals(new BigDecimal("1.79"), published.epsActual());
        assertEquals(new BigDecimal("8700000000"), published.revenueEstimate());
        assertEquals(new BigDecimal("8900000000"), published.revenueActual());

        FinnhubEarningsDto upcoming = earnings.get(1);
        assertEquals(LocalDate.of(2026, 12, 17), upcoming.date());
        assertEquals(2027, upcoming.year());
        assertNull(upcoming.epsEstimate());
        assertNull(upcoming.epsActual());
        assertNull(upcoming.revenueEstimate());
        assertNull(upcoming.revenueActual());
    }

    @Test
    void fetchEarnings_withAnEmptyBody_returnsAnEmptyList() {
        server.expect(requestTo(BASE_URL + "/calendar/earnings?symbol=MU&from=2026-09-01&to=2026-12-31"))
                .andRespond(withSuccess());

        List<FinnhubEarningsDto> earnings = client.fetchEarnings("MU", LocalDate.of(2026, 9, 1), LocalDate.of(2026, 12, 31));

        assertTrue(earnings.isEmpty());
    }

    @Test
    void fetchEarnings_withoutTheList_returnsAnEmptyList() {
        server.expect(requestTo(BASE_URL + "/calendar/earnings?symbol=MU&from=2026-09-01&to=2026-12-31"))
                .andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));

        List<FinnhubEarningsDto> earnings = client.fetchEarnings("MU", LocalDate.of(2026, 9, 1), LocalDate.of(2026, 12, 31));

        assertTrue(earnings.isEmpty());
    }
}

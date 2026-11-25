package com.llijat.t212visualizer.service;

import com.llijat.t212visualizer.dto.FinnhubCompanyProfileDto;
import com.llijat.t212visualizer.dto.FinnhubEarningsDto;
import com.llijat.t212visualizer.dto.FinnhubQuoteDto;
import com.llijat.t212visualizer.dto.LogoImage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.headerDoesNotExist;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/** MockRestServiceServer pretends to be Finnhub: no real request leaves the test. */
class LiveFinnhubClientTest {

    private static final String BASE_URL = "https://finnhub.example.test/api/v1";

    private static final String LOGO_URL = "https://static.example.test/logos/MU.png";
    private static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G'};

    private MockRestServiceServer server;
    private MockRestServiceServer downloadServer;
    private LiveFinnhubClient client;

    @BeforeEach
    void setUp() {
        // Like the real configuration: the API client carries the key, the download client does not
        RestClient.Builder builder = RestClient.builder().baseUrl(BASE_URL).defaultHeader("X-Finnhub-Token", "test-key");
        RestClient.Builder downloadBuilder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        downloadServer = MockRestServiceServer.bindTo(downloadBuilder).build();
        client = new LiveFinnhubClient(builder.build(), downloadBuilder.build());
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

    @Test
    void fetchCompanyProfile_readsTheNameAndTheLogoLink_andIgnoresTheOtherFields() {
        String json = """
                {"country": "US", "currency": "USD", "exchange": "NASDAQ", "finnhubIndustry": "Semiconductors",
                 "logo": "https://static.example.test/logos/MU.png", "marketCapitalization": 100000,
                 "name": "Micron Technology, Inc.", "ticker": "MU", "weburl": "https://example.test"}
                """;
        server.expect(requestTo(BASE_URL + "/stock/profile2?symbol=MU"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(json, MediaType.APPLICATION_JSON));

        FinnhubCompanyProfileDto profile = client.fetchCompanyProfile("MU");

        server.verify();
        assertEquals("Micron Technology, Inc.", profile.name());
        assertEquals("https://static.example.test/logos/MU.png", profile.logo());
    }

    @Test
    void fetchCompanyProfile_ofAnUnknownSymbol_leavesBothFieldsEmpty() {
        // Finnhub answers 200 with an empty object for symbols it does not know
        server.expect(requestTo(BASE_URL + "/stock/profile2?symbol=NOPE"))
                .andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));

        FinnhubCompanyProfileDto profile = client.fetchCompanyProfile("NOPE");

        assertNull(profile.name());
        assertNull(profile.logo());
    }

    @Test
    void downloadLogo_returnsTheImageAndItsType_withoutSendingTheApiKey() {
        downloadServer.expect(requestTo(LOGO_URL))
                .andExpect(method(HttpMethod.GET))
                .andExpect(headerDoesNotExist("X-Finnhub-Token"))
                .andRespond(withSuccess(PNG, MediaType.IMAGE_PNG));

        Optional<LogoImage> logo = client.downloadLogo(LOGO_URL);

        downloadServer.verify();
        assertTrue(logo.isPresent());
        assertArrayEquals(PNG, logo.get().bytes());
        assertEquals("image/png", logo.get().contentType());
    }

    @Test
    void downloadLogo_keepsOnlyTheMainType_withoutParameters() {
        downloadServer.expect(requestTo(LOGO_URL))
                .andRespond(withSuccess(PNG, MediaType.parseMediaType("image/png;charset=UTF-8")));

        Optional<LogoImage> logo = client.downloadLogo(LOGO_URL);

        assertEquals("image/png", logo.orElseThrow().contentType());
    }

    @Test
    void downloadLogo_rejectsAPageInsteadOfAnImage() {
        downloadServer.expect(requestTo(LOGO_URL))
                .andRespond(withSuccess("<script>alert(1)</script>", MediaType.TEXT_HTML));

        assertTrue(client.downloadLogo(LOGO_URL).isEmpty());
    }

    @Test
    void downloadLogo_rejectsSvg_becauseItCanContainScripts() {
        downloadServer.expect(requestTo(LOGO_URL))
                .andRespond(withSuccess("<svg><script>alert(1)</script></svg>", MediaType.parseMediaType("image/svg+xml")));

        assertTrue(client.downloadLogo(LOGO_URL).isEmpty());
    }

    @Test
    void downloadLogo_withoutContentType_returnsEmpty() {
        downloadServer.expect(requestTo(LOGO_URL))
                .andRespond(withStatus(HttpStatus.OK).body(PNG));

        assertTrue(client.downloadLogo(LOGO_URL).isEmpty());
    }

    @Test
    void downloadLogo_withAnEmptyBody_returnsEmpty() {
        downloadServer.expect(requestTo(LOGO_URL))
                .andRespond(withSuccess(new byte[0], MediaType.IMAGE_PNG));

        assertTrue(client.downloadLogo(LOGO_URL).isEmpty());
    }

    @Test
    void fetchQuote_asksForOneSymbol_withTheKey_andReadsTheMove() {
        server.expect(requestTo(BASE_URL + "/quote?symbol=MU"))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header("X-Finnhub-Token", "test-key"))
                .andRespond(withSuccess("""
                        {"c": 182.5, "d": -7.98, "dp": -4.19, "h": 190.1, "l": 181.9, "o": 189.7, "pc": 190.48, "t": 1794862800}
                        """, MediaType.APPLICATION_JSON));

        FinnhubQuoteDto quote = client.fetchQuote("MU");

        server.verify();
        assertEquals(new BigDecimal("-4.19"), quote.changePercent());
        assertEquals(1794862800L, quote.time());
        assertTrue(quote.isKnown());
    }
}

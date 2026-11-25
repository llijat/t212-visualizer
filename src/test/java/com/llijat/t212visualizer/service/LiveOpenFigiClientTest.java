package com.llijat.t212visualizer.service;

import com.llijat.t212visualizer.dto.OpenFigiListingDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/** MockRestServiceServer pretends to be OpenFIGI: no real request leaves the test. */
class LiveOpenFigiClientTest {

    private static final String BASE_URL = "https://openfigi.example.test";
    private static final String MAPPING_URL = BASE_URL + "/v3/mapping";

    private static final String META_ISIN = "US30303M1027";
    private static final String RWE_ISIN = "DE0007037129";
    private static final String UNKNOWN_ISIN = "XX0000000000";

    private MockRestServiceServer server;
    private LiveOpenFigiClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder().baseUrl(BASE_URL);
        server = MockRestServiceServer.bindTo(builder).build();
        client = new LiveOpenFigiClient(builder.build());
    }

    // What we expect to send: one job per ISIN, in the same order
    private static String jobsJson(List<String> isins) {
        return isins.stream()
                .map(isin -> "{\"idType\": \"ID_ISIN\", \"idValue\": \"" + isin + "\"}")
                .collect(Collectors.joining(",", "[", "]"));
    }

    // One answer per ISIN, in the same order, each with a single invented listing
    private static String oneListingEach(List<String> isins) {
        return isins.stream()
                .map(isin -> "{\"data\": [{\"ticker\": \"T" + isin + "\", \"exchCode\": \"US\", \"securityType\": \"Common Stock\", \"name\": \"X\"}]}")
                .collect(Collectors.joining(",", "[", "]"));
    }

    private static List<String> fakeIsins(int count) {
        List<String> isins = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            isins.add(String.format("US%010d", i)); // 12 characters, like a real ISIN
        }
        return isins;
    }

    @Test
    void sendsOneJobPerIsin_asJson_andGivesEachIsinAllItsListings() {
        // Arrange: Meta trades in many places (only three here); RWE in one.
        // A field OpenFIGI might add one day must not break anything
        String answer = """
                [
                  {"data": [
                    {"ticker": "META", "exchCode": "US", "securityType": "Common Stock", "name": "META PLATFORMS INC-CLASS A", "figi": "ignored"},
                    {"ticker": "META", "exchCode": "UW", "securityType": "Common Stock", "name": "META PLATFORMS INC-CLASS A"},
                    {"ticker": "FB2A", "exchCode": "GY", "securityType": "Common Stock", "name": "META PLATFORMS INC-CLASS A"}
                  ]},
                  {"data": [
                    {"ticker": "RWE", "exchCode": "GY", "securityType": "Common Stock", "name": "RWE AG"}
                  ]}
                ]
                """;
        server.expect(requestTo(MAPPING_URL))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(content().json(jobsJson(List.of(META_ISIN, RWE_ISIN)), true))
                .andRespond(withSuccess(answer, MediaType.APPLICATION_JSON));

        // Act
        Map<String, List<OpenFigiListingDto>> listings = client.findListings(List.of(META_ISIN, RWE_ISIN));

        // Assert: the first answer belongs to the first ISIN, the second to the second
        server.verify();
        assertEquals(2, listings.size());
        assertEquals(List.of("META", "META", "FB2A"), listings.get(META_ISIN).stream().map(OpenFigiListingDto::ticker).toList());
        assertEquals(new OpenFigiListingDto("RWE", "GY", "Common Stock", "RWE AG"), listings.get(RWE_ISIN).get(0));
        assertEquals(1, listings.get(RWE_ISIN).size());
    }

    @Test
    void anIsinOpenFigiDoesNotKnow_getsAnEmptyList_andTheOthersStillWork() {
        // When an ISIN is not found, "data" is missing and a "warning" comes instead
        String answer = """
                [
                  {"warning": "No identifier found."},
                  {"data": [{"ticker": "RWE", "exchCode": "GY", "securityType": "Common Stock", "name": "RWE AG"}]}
                ]
                """;
        server.expect(requestTo(MAPPING_URL)).andRespond(withSuccess(answer, MediaType.APPLICATION_JSON));

        Map<String, List<OpenFigiListingDto>> listings = client.findListings(List.of(UNKNOWN_ISIN, RWE_ISIN));

        assertTrue(listings.containsKey(UNKNOWN_ISIN));
        assertTrue(listings.get(UNKNOWN_ISIN).isEmpty());
        assertEquals("RWE", listings.get(RWE_ISIN).get(0).ticker());
    }

    @Test
    void twentyFiveIsins_goInThreeRequests_of10_10And5() {
        // Arrange: the requests must arrive in this order, each with its own slice
        List<String> isins = fakeIsins(25);
        for (List<String> batch : List.of(isins.subList(0, 10), isins.subList(10, 20), isins.subList(20, 25))) {
            server.expect(requestTo(MAPPING_URL))
                    .andExpect(content().json(jobsJson(batch), true))
                    .andRespond(withSuccess(oneListingEach(batch), MediaType.APPLICATION_JSON));
        }

        // Act
        Map<String, List<OpenFigiListingDto>> listings = client.findListings(isins);

        // Assert: exactly three requests, and every ISIN got its own answer, none mixed up
        server.verify();
        assertEquals(25, listings.size());
        for (String isin : isins) {
            assertEquals("T" + isin, listings.get(isin).get(0).ticker(), isin + " got someone else's answer");
        }
    }

    @Test
    void exactlyTenIsins_needOnlyOneRequest() {
        List<String> isins = fakeIsins(10);
        server.expect(requestTo(MAPPING_URL))
                .andExpect(content().json(jobsJson(isins), true))
                .andRespond(withSuccess(oneListingEach(isins), MediaType.APPLICATION_JSON));

        Map<String, List<OpenFigiListingDto>> listings = client.findListings(isins);

        server.verify(); // a second, empty request would fail here
        assertEquals(10, listings.size());
    }

    @Test
    void noIsins_sendNothing() {
        // No expectations: any request at all would fail the test
        Map<String, List<OpenFigiListingDto>> listings = client.findListings(List.of());

        server.verify();
        assertTrue(listings.isEmpty());
    }

    @Test
    void fewerAnswersThanIsins_failClearly_insteadOfPairingThemWrongly() {
        // Two ISINs sent, only one answer back: we cannot know which ISIN it belongs to
        server.expect(requestTo(MAPPING_URL))
                .andRespond(withSuccess(oneListingEach(List.of(META_ISIN)), MediaType.APPLICATION_JSON));

        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> client.findListings(List.of(META_ISIN, RWE_ISIN)));

        assertEquals("OpenFIGI answered 1 results for 2 ISINs", error.getMessage());
    }

    @Test
    void moreAnswersThanIsins_alsoFail() {
        // The dangerous case: without the check, the extra answer would go unnoticed
        server.expect(requestTo(MAPPING_URL))
                .andRespond(withSuccess(oneListingEach(List.of(META_ISIN, RWE_ISIN)), MediaType.APPLICATION_JSON));

        assertThrows(IllegalStateException.class, () -> client.findListings(List.of(META_ISIN)));
    }

    @Test
    void anEmptyBody_failsClearly() {
        server.expect(requestTo(MAPPING_URL)).andRespond(withSuccess());

        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> client.findListings(List.of(META_ISIN)));

        assertEquals("OpenFIGI answered 0 results for 1 ISINs", error.getMessage());
    }
}

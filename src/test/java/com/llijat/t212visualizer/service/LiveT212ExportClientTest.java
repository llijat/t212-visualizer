package com.llijat.t212visualizer.service;

import com.llijat.t212visualizer.dto.T212ExportReportDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.headerDoesNotExist;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/** MockRestServiceServer pretends to be the server: no real request leaves the test. */
class LiveT212ExportClientTest {

    private static final String BASE_URL = "https://t212.example.test/api/v0";

    private MockRestServiceServer apiServer;
    private MockRestServiceServer downloadServer;
    private LiveT212ExportClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder apiBuilder = RestClient.builder()
                .baseUrl(BASE_URL)
                .defaultHeaders(headers -> headers.setBasicAuth("key", "secret"));
        RestClient.Builder downloadBuilder = RestClient.builder();
        apiServer = MockRestServiceServer.bindTo(apiBuilder).build();
        downloadServer = MockRestServiceServer.bindTo(downloadBuilder).build();
        client = new LiveT212ExportClient(apiBuilder.build(), downloadBuilder.build());
    }

    @Test
    void requestExport_asksOnlyForTransactions_inTheGivenPeriod() {
        // Arrange
        apiServer.expect(requestTo(BASE_URL + "/equity/history/exports"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(jsonPath("$.timeFrom").value("2026-10-01T00:00:00Z"))
                .andExpect(jsonPath("$.timeTo").value("2026-11-01T00:00:00Z"))
                .andExpect(jsonPath("$.dataIncluded.includeTransactions").value(true))
                .andExpect(jsonPath("$.dataIncluded.includeOrders").value(false))
                .andExpect(jsonPath("$.dataIncluded.includeDividends").value(false))
                .andExpect(jsonPath("$.dataIncluded.includeInterest").value(false))
                .andRespond(withSuccess("{\"reportId\": 42}", MediaType.APPLICATION_JSON));

        // Act
        long reportId = client.requestExport(Instant.parse("2026-10-01T00:00:00Z"), Instant.parse("2026-11-01T00:00:00Z"));

        // Assert
        assertEquals(42L, reportId);
        apiServer.verify();
    }

    @Test
    void fetchExports_readsTheStatusAndTheLink() {
        // Arrange: a report not finished yet has no link
        apiServer.expect(requestTo(BASE_URL + "/equity/history/exports"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("""
                        [
                          {"reportId": 1, "status": "Finished", "downloadLink": "https://downloads.example.test/1.csv"},
                          {"reportId": 2, "status": "Running"}
                        ]
                        """, MediaType.APPLICATION_JSON));

        // Act
        List<T212ExportReportDto> reports = client.fetchExports();

        // Assert
        assertEquals(2, reports.size());
        assertEquals(1L, reports.get(0).reportId());
        assertEquals("Finished", reports.get(0).status());
        assertEquals("https://downloads.example.test/1.csv", reports.get(0).downloadLink());
        assertNull(reports.get(1).downloadLink());
    }

    @Test
    void downloadExport_neverSendsTheApiCredentials_toTheDownloadHost() {
        // Arrange: the link points outside Trading 212. If this request carried the key, a third party would receive it.
        String link = "https://downloads.example.test/report.csv";
        downloadServer.expect(requestTo(link))
                .andExpect(method(HttpMethod.GET))
                .andExpect(headerDoesNotExist(HttpHeaders.AUTHORIZATION))
                .andRespond(withSuccess("the csv", MediaType.TEXT_PLAIN));

        // Act
        String csv = client.downloadExport(link);

        // Assert
        assertEquals("the csv", csv);
        downloadServer.verify();
    }

    @Test
    void downloadExport_keepsTheSignedLinkExactlyAsItCame() {
        // Arrange: the link is signed. Encoding its %2F a second time would make the signature invalid.
        String link = "https://downloads.example.test/report.csv?credential=abc%2F20261103&signature=x%2By";
        downloadServer.expect(requestTo(URI.create(link)))
                .andRespond(withSuccess("the csv", MediaType.TEXT_PLAIN));

        // Act
        client.downloadExport(link);

        // Assert
        downloadServer.verify();
    }

    @Test
    void downloadExport_readsTheFileAsUtf8() {
        // Arrange: without a declared charset, an accent in a merchant's name could arrive broken
        byte[] body = "Café Niño".getBytes(StandardCharsets.UTF_8);
        downloadServer.expect(requestTo("https://downloads.example.test/report.csv"))
                .andRespond(withSuccess(body, MediaType.parseMediaType("text/csv")));

        // Act
        String csv = client.downloadExport("https://downloads.example.test/report.csv");

        // Assert
        assertEquals("Café Niño", csv);
    }

    @Test
    void apiRequests_doSendTheCredentials() {
        // Arrange: the other side of the coin: the API does need them
        apiServer.expect(requestTo(BASE_URL + "/equity/history/exports"))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Basic a2V5OnNlY3JldA=="))
                .andRespond(withSuccess("[]", MediaType.APPLICATION_JSON));

        // Act
        client.fetchExports();

        // Assert
        apiServer.verify();
    }
}

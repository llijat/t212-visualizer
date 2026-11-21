package com.llijat.t212visualizer.config;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The real configuration, against a tiny server on this computer (nothing leaves it).
 * Alpha Vantage only takes the key in the URL, so these tests make sure it gets there and never into an error message.
 */
class AlphaVantageClientConfigTest {

    private static final String KEY = "TESTKEY123";

    private HttpServer server;

    @AfterEach
    void stopServer() {
        if (server != null) server.stop(0);
    }

    private RestClient clientFor(String baseUrl) {
        AlphaVantageClientConfig config = new AlphaVantageClientConfig();
        // @Value fills these in the app; here they are set by hand
        ReflectionTestUtils.setField(config, "baseUrl", baseUrl);
        ReflectionTestUtils.setField(config, "apiKey", KEY);
        return config.alphaVantageRestClient();
    }

    @Test
    void theKey_isAddedToEveryRequest_afterTheOtherParameters() throws IOException {
        AtomicReference<String> receivedQuery = new AtomicReference<>();
        server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        server.createContext("/query", exchange -> {
            receivedQuery.set(exchange.getRequestURI().getRawQuery());
            byte[] body = "{\"data\": []}".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();

        clientFor("http://127.0.0.1:" + server.getAddress().getPort())
                .get().uri("/query?function=DIVIDENDS&symbol={symbol}", "MU")
                .retrieve().toBodilessEntity();

        assertEquals("function=DIVIDENDS&symbol=MU&apikey=" + KEY, receivedQuery.get());
    }

    @Test
    void whenTheConnectionFails_theErrorMessage_doesNotShowTheKey() throws IOException {
        // A port that was free a moment ago: nothing answers there, so the connection is refused
        int closedPort;
        try (ServerSocket socket = new ServerSocket(0, 1, InetAddress.getLoopbackAddress())) {
            closedPort = socket.getLocalPort();
        }
        RestClient client = clientFor("http://127.0.0.1:" + closedPort);

        ResourceAccessException error = assertThrows(ResourceAccessException.class, () ->
                client.get().uri("/query?function=DIVIDENDS&symbol={symbol}", "MU").retrieve().toBodilessEntity());

        // The message names the address. Spring 6.1 already leaves the parameters out of it, and the interceptor
        // keeps the key out of the request's URL anyway: this test holds whichever of the two protects it.
        assertTrue(error.getMessage().contains("/query"), error.getMessage());
        assertFalse(error.getMessage().contains(KEY), error.getMessage());
    }
}

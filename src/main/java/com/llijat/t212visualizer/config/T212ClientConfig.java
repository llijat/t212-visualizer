package com.llijat.t212visualizer.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

@Configuration
public class T212ClientConfig {

    @Value("${t212.base-url}")
    private String baseUrl;

    @Value("${t212.api-key}")
    private String apiKey;

    @Value("${t212.api-secret}")
    private String apiSecret;

    /**
     * Confirmed against the official docs (docs.trading212.com): auth is
     * HTTP Basic, API_KEY as username, API_SECRET as password, i.e.
     * Base64("API_KEY:API_SECRET") in the Authorization header.
     * setBasicAuth() below does that encoding for you.
     */
    @Bean
    public RestClient t212RestClient() {
        return RestClient.builder()
                .baseUrl(baseUrl)
                .defaultHeaders(headers -> headers.setBasicAuth(apiKey, apiSecret))
                .build();
    }
}

package com.llijat.t212visualizer.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.time.Duration;

@Configuration
@Profile("!demo")
public class FinnhubClientConfig {
    @Value("${finnhub.base-url}")
    private String baseUrl;

    @Value("${finnhub.api-key}")
    private String apiKey;

    @Bean
    public RestClient finnhubRestClient() {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofSeconds(5));
        requestFactory.setReadTimeout(Duration.ofSeconds(10));
        return RestClient.builder()
                .baseUrl(baseUrl)
                .requestFactory(requestFactory)
                .defaultHeader("X-Finnhub-token", apiKey)
                .build();
    }
}

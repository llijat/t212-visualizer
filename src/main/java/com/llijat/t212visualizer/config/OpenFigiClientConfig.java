package com.llijat.t212visualizer.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.time.Duration;

@Configuration
@Profile("!demo")
public class OpenFigiClientConfig {
    private static final String BASE_URL = "https://api.openfigi.com";


    @Bean
    public RestClient openFigiRestClient() {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofSeconds(5));
        requestFactory.setReadTimeout(Duration.ofSeconds(15));
        return RestClient.builder()
                .baseUrl(BASE_URL)
                .requestFactory(requestFactory)
                .build();
    }
}

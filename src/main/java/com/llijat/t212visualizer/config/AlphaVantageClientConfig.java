package com.llijat.t212visualizer.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.http.client.support.HttpRequestWrapper;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.IOException;
import java.net.URI;
import java.time.Duration;

@Configuration
@Profile("!demo")
public class AlphaVantageClientConfig {
    @Value("${alpha-vantage.base-url}")
    private String baseUrl;

    @Value("${alpha-vantage.api-key}")
    private String apiKey;

    @Bean
    public RestClient alphaVantageRestClient() {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofSeconds(5));
        requestFactory.setReadTimeout(Duration.ofSeconds(15));
        return RestClient.builder()
                .baseUrl(baseUrl)
                .requestFactory(requestFactory)
                .requestInterceptor(this::addApiKey)
                .build();
    }

    /** Alpha Vantage only takes the key in the URL: it is added at the last moment, so error messages and logs never show it. */
    private ClientHttpResponse addApiKey(HttpRequest request, byte[] body, ClientHttpRequestExecution execution) throws IOException {
        URI withKey = UriComponentsBuilder.fromUri(request.getURI())
                .queryParam("apikey", apiKey)
                .build(true)
                .toUri();
        return execution.execute(new HttpRequestWrapper(request) {
            @Override
            public URI getURI() {
                return withKey;
            }
        }, body);
    }

}

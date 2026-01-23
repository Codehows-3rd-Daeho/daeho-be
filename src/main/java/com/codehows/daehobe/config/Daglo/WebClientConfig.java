package com.codehows.daehobe.config.Daglo;
/*
* 외부 api 설정
* */

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.ExchangeStrategies;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;


import java.time.Duration;

@Configuration

public class WebClientConfig {

    @Bean
    public WebClient dagloWebClient(DagloProperties dagloProperties) {

        HttpClient httpClient = HttpClient.create()
                .responseTimeout(Duration.ofSeconds(dagloProperties.getTimeout()));

        ExchangeStrategies exchangeStrategies = ExchangeStrategies.builder()
                .codecs(configurer ->
                        configurer.defaultCodecs()
                                .maxInMemorySize(256 * 1024 * 1024) // 최대 256MB
                )
                .build();


        return WebClient.builder()
                .baseUrl(dagloProperties.getBaseUrl())
                .defaultHeader(
                        HttpHeaders.AUTHORIZATION,
                        "Bearer " + dagloProperties.getToken()
                )
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .exchangeStrategies(exchangeStrategies)
                .build();
    }
}

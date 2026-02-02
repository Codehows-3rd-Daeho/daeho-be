package com.codehows.daehobe.config.Daglo;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.time.Duration;

/**
 * Daglo API용 RestClient 설정
 * WebClient에서 RestClient로 마이그레이션 - 동기식 HTTP 클라이언트
 */
@Configuration
public class RestClientConfig {

    @Bean
    public RestClient dagloRestClient(DagloProperties dagloProperties) {
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory();
        requestFactory.setReadTimeout(Duration.ofSeconds(dagloProperties.getTimeout()));

        return RestClient.builder()
                .baseUrl(dagloProperties.getBaseUrl())
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + dagloProperties.getToken())
                .requestFactory(requestFactory)
                .build();
    }
}

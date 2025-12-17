package com.codehows.daehobe.config.stt;
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
    //다글로 설정
    public WebClient dagloWebClient(DagloProperties dagloProperties) {

        // HttpClient: HTTP 통신을 담당
        //HttpClient 생성
        HttpClient httpClient = HttpClient.create()
                .responseTimeout(Duration.ofSeconds(dagloProperties.getTimeout()));//서버 응답을 기다리는 최대 시간 설정

        // 응답 버퍼 크기(용량) 설정
        ExchangeStrategies exchangeStrategies = ExchangeStrategies.builder()
                .codecs(configurer ->
                        configurer.defaultCodecs()
                                .maxInMemorySize(100 * 1024 * 1024) // 100MB
                )
                .build();


        return WebClient.builder()
                .baseUrl(dagloProperties.getBaseUrl())
                .defaultHeader(
                        HttpHeaders.AUTHORIZATION,
                        "Bearer " + dagloProperties.getToken()
                )// ==> 모든 Daglo Api 요청에 자동으로 token 포함
                .clientConnector(new ReactorClientHttpConnector(httpClient))//HttpClient를 WebClient에 연결
                .exchangeStrategies(exchangeStrategies)//응답 용량 크기 설정
                .build();
    }
}

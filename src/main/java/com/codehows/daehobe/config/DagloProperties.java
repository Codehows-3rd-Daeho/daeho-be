package com.codehows.daehobe.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/*
다글로 설정 값
 */
@Component
//application.properties에 있는 설정과 자동 매핑
@ConfigurationProperties(prefix = "daglo.api")
@Getter
@Setter
public class DagloProperties {
    private String token;
    private String baseUrl;
    private int timeout;
}

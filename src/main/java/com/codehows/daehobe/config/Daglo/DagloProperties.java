package com.codehows.daehobe.config.Daglo;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "daglo.api")
@Getter
@Setter
public class DagloProperties {
    private String token;
    private String baseUrl;
    private int timeout;
}

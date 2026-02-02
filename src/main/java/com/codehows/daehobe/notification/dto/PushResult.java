package com.codehows.daehobe.notification.dto;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class PushResult {
    private final boolean success;
    private final String endpoint;
    private final String errorMessage;
    private final int statusCode;
    private final long latencyMs;

    public static PushResult success(String endpoint, int statusCode, long latencyMs) {
        return PushResult.builder()
                .success(true)
                .endpoint(endpoint)
                .statusCode(statusCode)
                .latencyMs(latencyMs)
                .build();
    }

    public static PushResult failure(String endpoint, String errorMessage, long latencyMs) {
        return PushResult.builder()
                .success(false)
                .endpoint(endpoint)
                .errorMessage(errorMessage)
                .latencyMs(latencyMs)
                .build();
    }

    public static PushResult failure(String endpoint, String errorMessage, int statusCode, long latencyMs) {
        return PushResult.builder()
                .success(false)
                .endpoint(endpoint)
                .errorMessage(errorMessage)
                .statusCode(statusCode)
                .latencyMs(latencyMs)
                .build();
    }
}

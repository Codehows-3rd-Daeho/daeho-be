package com.codehows.daehobe.common.dto;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class RedisWebSocketMessage {
    private String destination;

    @JsonTypeInfo(use = JsonTypeInfo.Id.CLASS)
    private Object payload;
}
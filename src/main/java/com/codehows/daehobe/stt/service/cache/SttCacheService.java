package com.codehows.daehobe.stt.service.cache;

import com.codehows.daehobe.stt.dto.STTDto;
import com.codehows.daehobe.stt.entity.STT;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class SttCacheService {
    private final StringRedisTemplate hashRedisTemplate;
    private final ObjectMapper objectMapper;

    private static final String STT_STATUS_PREFIX = "stt:status:";

    public void cacheSttStatus(STTDto sttDto) {
        try {
            String key = STT_STATUS_PREFIX + sttDto.getId();
            String jsonValue = objectMapper.writeValueAsString(sttDto);
            long ttlMinutes = calculateTtl(sttDto.getStatus());

            hashRedisTemplate.opsForValue().set(key, jsonValue, ttlMinutes, TimeUnit.MINUTES);

            log.debug("STT status cached - ID: {}, TTL: {} minutes", sttDto.getId(), ttlMinutes);
        } catch (Exception e) {
            log.error("Failed to cache STT status for ID: {}", sttDto.getId(), e);
        }
    }

    private long calculateTtl(STT.Status status) {
        return switch (status) {
            case RECORDING, ENCODING -> 60;     // 진행 중: 1시간
            case ENCODED -> 1440;               // 대기 중: 24시간 (사용자 액션 대기)
            case PROCESSING, SUMMARIZING -> 30; // API 폴링 중: 30분
            case COMPLETED -> 10;               // 완료: 10분 (DB에 이미 저장됨)
        };
    }

    public STTDto getCachedSttStatus(Long sttId) {
        try {
            String key = STT_STATUS_PREFIX + sttId;
            String jsonValue = hashRedisTemplate.opsForValue().get(key);

            return jsonValue != null ? objectMapper.readValue(jsonValue, STTDto.class) : null;
        } catch (Exception e) {
            log.error("Failed to get STT status for ID: {}", sttId, e);
            return null;
        }
    }
}

package com.codehows.daehobe.stt.service.cache;

import com.codehows.daehobe.stt.dto.STTDto;
import com.codehows.daehobe.stt.entity.STT;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static com.codehows.daehobe.stt.constant.SttRedisKeys.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class SttCacheService {
    private final StringRedisTemplate hashRedisTemplate;
    private final ObjectMapper objectMapper;

    private static final String STT_STATUS_PREFIX = "stt:status:";

    @Value("${stt.polling.stale-threshold-minutes:60}")
    private long staleThresholdMinutes;

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

    public void addToPollingSet(Long sttId, STT.Status status) {
        String setKey = getPollingSetKey(status);
        if (setKey != null) {
            double score = System.currentTimeMillis();
            hashRedisTemplate.opsForZSet().add(setKey, String.valueOf(sttId), score);
            log.debug("Added STT {} to polling ZSet: {} with score: {}", sttId, setKey, score);
        }
    }

    public void removeFromPollingSet(Long sttId, STT.Status status) {
        String setKey = getPollingSetKey(status);
        if (setKey != null) {
            hashRedisTemplate.opsForZSet().remove(setKey, String.valueOf(sttId));
            log.debug("Removed STT {} from polling ZSet: {}", sttId, setKey);
        }
    }

    public Set<Long> getPollingTaskIds(STT.Status status) {
        String setKey = getPollingSetKey(status);
        if (setKey == null) {
            return Collections.emptySet();
        }

        try {
            Set<String> members = hashRedisTemplate.opsForZSet().range(setKey, 0, -1);
            if (members == null || members.isEmpty()) {
                return Collections.emptySet();
            }

            return members.stream()
                    .map(Long::valueOf)
                    .collect(Collectors.toSet());
        } catch (Exception e) {
            log.warn("Redis unavailable for polling ZSet {}. Exception: {}", setKey, e.getMessage());
            return Collections.emptySet();
        }
    }

    @Scheduled(fixedDelayString = "${stt.polling.cleanup-interval-ms:600000}")
    public void cleanupStalePollingTasks() {
        long thresholdTime = System.currentTimeMillis() - (staleThresholdMinutes * 60 * 1000);

        cleanupStaleTasksFromSet(STT_POLLING_PROCESSING_SET, thresholdTime);
        cleanupStaleTasksFromSet(STT_POLLING_SUMMARIZING_SET, thresholdTime);
    }

    private void cleanupStaleTasksFromSet(String setKey, long thresholdTime) {
        try {
            Long removedCount = hashRedisTemplate.opsForZSet()
                    .removeRangeByScore(setKey, 0, thresholdTime);

            if (removedCount != null && removedCount > 0) {
                log.info("Cleaned up {} stale polling tasks from {}", removedCount, setKey);
            }
        } catch (Exception e) {
            log.warn("Failed to cleanup stale tasks from {}: {}", setKey, e.getMessage());
        }
    }

    public boolean isRedisAvailable() {
        try {
            hashRedisTemplate.hasKey("health-check");
            return true;
        } catch (Exception e) {
            log.warn("Redis health check failed: {}", e.getMessage());
            return false;
        }
    }

    public int incrementRetryCount(Long sttId) {
        String key = STT_RETRY_COUNT_PREFIX + sttId;
        Long newValue = hashRedisTemplate.opsForValue().increment(key);
        hashRedisTemplate.expire(key, 30, TimeUnit.MINUTES);
        return newValue != null ? newValue.intValue() : 1;
    }

    public Integer getRetryCount(Long sttId) {
        String key = STT_RETRY_COUNT_PREFIX + sttId;
        String value = hashRedisTemplate.opsForValue().get(key);
        return value != null ? Integer.valueOf(value) : 0;
    }

    public void resetRetryCount(Long sttId) {
        String key = STT_RETRY_COUNT_PREFIX + sttId;
        hashRedisTemplate.delete(key);
    }

    private String getPollingSetKey(STT.Status status) {
        return switch (status) {
            case PROCESSING -> STT_POLLING_PROCESSING_SET;
            case SUMMARIZING -> STT_POLLING_SUMMARIZING_SET;
            default -> null;
        };
    }
}

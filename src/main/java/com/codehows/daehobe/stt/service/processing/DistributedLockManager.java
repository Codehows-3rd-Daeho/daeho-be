package com.codehows.daehobe.stt.service.processing;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

@Component
@RequiredArgsConstructor
@Slf4j
public class DistributedLockManager {

    private final RedisTemplate<String, String> redisTemplate;
    private static final long DEFAULT_LOCK_TTL_SECONDS = 5;

    public boolean acquireLock(String lockKey) {
        return acquireLock(lockKey, DEFAULT_LOCK_TTL_SECONDS);
    }

    public boolean acquireLock(String lockKey, long ttlSeconds) {
        Boolean acquired = redisTemplate.opsForValue()
                .setIfAbsent(lockKey, "locked", ttlSeconds, TimeUnit.SECONDS);

        if (Boolean.TRUE.equals(acquired)) {
            log.debug("Acquired distributed lock: {}", lockKey);
            return true;
        }

        log.debug("Failed to acquire distributed lock: {}", lockKey);
        return false;
    }

    public void releaseLock(String lockKey) {
        redisTemplate.delete(lockKey);
        log.debug("Released distributed lock: {}", lockKey);
    }
}
package com.codehows.daehobe.notification.webPush.service;

import com.codehows.daehobe.notification.dto.PushSubscriptionDto;
import com.codehows.daehobe.notification.exception.InvalidSubscriptionException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Optional;

/**
 * 푸시 구독 관리 전용 서비스
 * 책임: 구독 저장, 삭제, 조회, 유효성 검증
 *
 * 저장 구조: String 키 (TTL 적용 가능)
 * 키 패턴: web-push:subscription:{memberId}
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PushSubscriptionService {

    private static final String SUBSCRIPTION_KEY_PREFIX = "web-push:subscription:";

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    @Value("${notification.subscription.ttl-days:7}")
    private int subscriptionTtlDays;

    public void saveSubscription(PushSubscriptionDto subscriptionDto, String memberId) {
        validateSubscription(subscriptionDto);

        try {
            String subscriptionJson = objectMapper.writeValueAsString(subscriptionDto);
            String key = buildKey(memberId);
            redisTemplate.opsForValue().set(key, subscriptionJson, Duration.ofDays(subscriptionTtlDays));
            log.info("Subscription saved for member {} with TTL {} days", memberId, subscriptionTtlDays);
        } catch (JsonProcessingException e) {
            log.error("Error saving subscription for member {}", memberId, e);
            throw new RuntimeException("Failed to save subscription", e);
        }
    }

    public Optional<PushSubscriptionDto> getSubscription(String memberId) {
        String key = buildKey(memberId);
        String subscriptionJson = redisTemplate.opsForValue().get(key);

        if (subscriptionJson == null) {
            return Optional.empty();
        }

        try {
            return Optional.of(objectMapper.readValue(subscriptionJson, PushSubscriptionDto.class));
        } catch (JsonProcessingException e) {
            log.error("Error deserializing subscription for member {}", memberId, e);
            return Optional.empty();
        }
    }

    public void deleteSubscription(String memberId) {
        String key = buildKey(memberId);
        redisTemplate.delete(key);
        log.info("Subscription deleted for member {}", memberId);
    }

    public boolean hasSubscription(String memberId) {
        String key = buildKey(memberId);
        return Boolean.TRUE.equals(redisTemplate.hasKey(key));
    }

    /**
     * 구독 TTL 갱신 (사용자 활동 시 호출)
     */
    public void refreshSubscriptionTtl(String memberId) {
        String key = buildKey(memberId);
        if (Boolean.TRUE.equals(redisTemplate.hasKey(key))) {
            redisTemplate.expire(key, Duration.ofDays(subscriptionTtlDays));
            log.debug("Subscription TTL refreshed for member {}", memberId);
        }
    }

    private String buildKey(String memberId) {
        return SUBSCRIPTION_KEY_PREFIX + memberId;
    }

    private void validateSubscription(PushSubscriptionDto dto) {
        if (dto == null) {
            throw new InvalidSubscriptionException("Subscription cannot be null");
        }

        if (!isValidEndpoint(dto.getEndpoint())) {
            throw new InvalidSubscriptionException("Invalid endpoint URL: " + dto.getEndpoint());
        }

        if (dto.getKeys() == null) {
            throw new InvalidSubscriptionException("Missing encryption keys");
        }

        if (dto.getKeys().getP256dh() == null || dto.getKeys().getP256dh().isBlank()) {
            throw new InvalidSubscriptionException("Missing p256dh key");
        }

        if (dto.getKeys().getAuth() == null || dto.getKeys().getAuth().isBlank()) {
            throw new InvalidSubscriptionException("Missing auth key");
        }
    }

    private boolean isValidEndpoint(String endpoint) {
        if (endpoint == null || endpoint.isBlank()) {
            return false;
        }

        try {
            java.net.URI uri = java.net.URI.create(endpoint);
            String scheme = uri.getScheme();
            return "https".equalsIgnoreCase(scheme);
        } catch (IllegalArgumentException e) {
            return false;
        }
    }
}

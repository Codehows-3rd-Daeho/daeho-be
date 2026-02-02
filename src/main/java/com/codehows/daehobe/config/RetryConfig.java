package com.codehows.daehobe.config;

import org.jose4j.lang.JoseException;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.retry.annotation.EnableRetry;
import org.springframework.retry.backoff.ExponentialRandomBackOffPolicy;
import org.springframework.retry.policy.SimpleRetryPolicy;
import org.springframework.retry.support.RetryTemplate;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.HashMap;
import java.util.Map;

/**
 * Retry 설정 클래스
 * Jitter(무작위 지연)를 적용하여 대량 재시도 시 서버 부하 분산
 */
@Configuration
@EnableRetry
public class RetryConfig {

    /**
     * WebPush 전용 RetryTemplate
     * - 최대 3회 재시도
     * - Exponential Backoff + Jitter 적용
     * - 기본 간격: 1초, 최대 간격: 10초, 배수: 2.0
     */
    @Bean
    public RetryTemplate webPushRetryTemplate() {
        RetryTemplate retryTemplate = new RetryTemplate();

        // Exponential Random Backoff Policy (Jitter 포함)
        ExponentialRandomBackOffPolicy backOffPolicy = new ExponentialRandomBackOffPolicy();
        backOffPolicy.setInitialInterval(1000L);  // 초기 대기 시간: 1초
        backOffPolicy.setMultiplier(2.0);          // 배수: 2배씩 증가
        backOffPolicy.setMaxInterval(10000L);      // 최대 대기 시간: 10초
        // ExponentialRandomBackOffPolicy는 자동으로 0~interval 사이의 random jitter 적용
        retryTemplate.setBackOffPolicy(backOffPolicy);

        // 재시도 대상 예외 설정
        Map<Class<? extends Throwable>, Boolean> retryableExceptions = new HashMap<>();
        retryableExceptions.put(IOException.class, true);
        retryableExceptions.put(GeneralSecurityException.class, true);
        retryableExceptions.put(JoseException.class, true);
        retryableExceptions.put(RuntimeException.class, true);

        SimpleRetryPolicy retryPolicy = new SimpleRetryPolicy(3, retryableExceptions);
        retryTemplate.setRetryPolicy(retryPolicy);

        return retryTemplate;
    }
}

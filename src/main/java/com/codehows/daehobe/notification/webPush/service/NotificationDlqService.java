package com.codehows.daehobe.notification.webPush.service;

import com.codehows.daehobe.notification.dto.FailedNotificationDto;
import com.codehows.daehobe.notification.dto.NotificationMessageDto;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * 알림 Dead Letter Queue 관리 서비스
 * 책임: DLQ 저장, 조회, 배치 재처리
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationDlqService {

    private static final String NOTIFICATION_DLQ_KEY = "notification:dlq";
    private static final int MAX_RETRY_COUNT = 5;

    private final RedisTemplate<String, Object> redisTemplate;
    private final ObjectMapper objectMapper;
    private final WebPushSender webPushSender;

    @Value("${notification.dlq.batch-size:50}")
    private int batchSize;

    public void saveToDeadLetterQueue(String memberId, NotificationMessageDto messageDto,
                                      int statusCode, String errorMessage) {
        try {
            FailedNotificationDto failedNotification = FailedNotificationDto.of(
                    memberId, messageDto, statusCode, errorMessage
            );
            String json = objectMapper.writeValueAsString(failedNotification);
            redisTemplate.opsForList().rightPush(NOTIFICATION_DLQ_KEY, json);
            log.info("Failed notification saved to DLQ for member {}", memberId);
        } catch (JsonProcessingException e) {
            log.error("Failed to save notification to DLQ for member {}", memberId, e);
        }
    }

    public void saveToDeadLetterQueue(FailedNotificationDto failedNotification) {
        try {
            String json = objectMapper.writeValueAsString(failedNotification);
            redisTemplate.opsForList().rightPush(NOTIFICATION_DLQ_KEY, json);
            log.info("Failed notification saved to DLQ for member {}", failedNotification.getMemberId());
        } catch (JsonProcessingException e) {
            log.error("Failed to save notification to DLQ for member {}", failedNotification.getMemberId(), e);
        }
    }

    public Long getDlqSize() {
        return redisTemplate.opsForList().size(NOTIFICATION_DLQ_KEY);
    }

    public List<FailedNotificationDto> peekDlqItems(int count) {
        List<FailedNotificationDto> items = new ArrayList<>();
        List<Object> rawItems = redisTemplate.opsForList().range(NOTIFICATION_DLQ_KEY, 0, count - 1);

        if (rawItems == null) {
            return items;
        }

        for (Object rawItem : rawItems) {
            try {
                items.add(objectMapper.readValue((String) rawItem, FailedNotificationDto.class));
            } catch (JsonProcessingException e) {
                log.error("Failed to deserialize DLQ item", e);
            }
        }
        return items;
    }

    /**
     * DLQ 배치 재처리 스케줄러
     * 5분마다 실행하며, DLQ에 있는 실패한 알림들을 배치로 재처리
     */
    @Scheduled(fixedDelayString = "${notification.dlq.process-interval:300000}")
    public void processDlqBatch() {
        Long queueSize = getDlqSize();
        if (queueSize == null || queueSize == 0) {
            return;
        }

        log.info("Starting DLQ batch processing. Queue size: {}", queueSize);
        int processedCount = 0;
        int successCount = 0;
        int permanentFailCount = 0;

        int itemsToProcess = (int) Math.min(batchSize, queueSize);
        for (int i = 0; i < itemsToProcess; i++) {
            Object rawItem = redisTemplate.opsForList().leftPop(NOTIFICATION_DLQ_KEY);
            if (rawItem == null) {
                break;
            }

            try {
                FailedNotificationDto failedNotification = objectMapper.readValue(
                        (String) rawItem, FailedNotificationDto.class
                );

                if (shouldRetry(failedNotification)) {
                    boolean success = retryNotification(failedNotification);
                    if (success) {
                        successCount++;
                    } else {
                        // 재시도 실패 시 retryCount 증가 후 다시 DLQ에 저장
                        saveToDeadLetterQueue(failedNotification.withIncrementedRetryCount());
                    }
                } else {
                    // 최대 재시도 횟수 초과 - 영구 실패로 처리
                    log.warn("Notification permanently failed for member {}. Max retries exceeded.",
                            failedNotification.getMemberId());
                    permanentFailCount++;
                }
                processedCount++;
            } catch (JsonProcessingException e) {
                log.error("Failed to deserialize DLQ item", e);
            }
        }

        log.info("DLQ batch processing completed. Processed: {}, Success: {}, Permanent failures: {}",
                processedCount, successCount, permanentFailCount);
    }

    private boolean shouldRetry(FailedNotificationDto notification) {
        // 410 (구독 만료)는 재시도하지 않음
        if (notification.getStatusCode() == 410) {
            return false;
        }
        // 최대 재시도 횟수 확인
        return notification.getRetryCount() < MAX_RETRY_COUNT;
    }

    private boolean retryNotification(FailedNotificationDto failedNotification) {
        try {
            NotificationMessageDto messageDto = new NotificationMessageDto();
            messageDto.setMessage(failedNotification.getMessage());
            messageDto.setUrl(failedNotification.getUrl());

            webPushSender.sendPushNotification(failedNotification.getMemberId(), messageDto);
            log.info("DLQ retry successful for member {}", failedNotification.getMemberId());
            return true;
        } catch (Exception e) {
            log.warn("DLQ retry failed for member {}: {}", failedNotification.getMemberId(), e.getMessage());
            return false;
        }
    }
}

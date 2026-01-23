/**
 * @file NotificationConsumer.java
 * @description Kafka의 "notification-topic"으로부터 메시지를 소비(consume)하고,
 * 수신된 메시지를 파싱하여 웹 푸시 알림을 전송하는 컨슈머 클래스입니다.
 */

package com.codehows.daehobe.notification.webPush.service;

import com.codehows.daehobe.notification.dto.NotificationMessageDto;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

import static com.codehows.daehobe.common.constant.KafkaConstants.NOTIFICATION_GROUP;
import static com.codehows.daehobe.common.constant.KafkaConstants.NOTIFICATION_TOPIC;

/**
 * @class NotificationConsumer
 * @description Kafka의 "notification-topic"으로부터 알림 메시지를 소비하고,
 *              이를 웹 푸시 알림으로 변환하여 사용자에게 전송하는 컨슈머 컴포넌트입니다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WebPushConsumer {

    private final WebPushService webPushService;
    private final ObjectMapper objectMapper;

    /**
     * @method listen
     * @description Kafka의 "notification-topic"으로부터 메시지를 리스닝하는 메서드입니다.
     *              수신된 메시지의 키(memberId)와 값을 파싱하여 웹 푸시 알림을 전송합니다.
     * @param {String} memberId - Kafka 메시지의 키로 받은 사용자 ID
     * @param {String} message - Kafka로부터 수신된 JSON 형태의 알림 메시지 문자열
     */
    @KafkaListener(topics = NOTIFICATION_TOPIC,
            groupId = NOTIFICATION_GROUP,
            containerFactory = "notificationListenerContainerFactory"
    )
    public void listen(@Header(KafkaHeaders.RECEIVED_KEY) String memberId, String message) {
        log.info("Received notification from Kafka for memberId: {}, message: {}", memberId, message);
        try {
            NotificationMessageDto messageDto = objectMapper.readValue(message, NotificationMessageDto.class);
            String notificationMessage = messageDto.getMessage();

            if (memberId != null && notificationMessage != null) {
                webPushService.sendNotificationToUser(memberId, messageDto);
                log.info("Notification sent to user {}: {}, url:{}", memberId, messageDto.getMessage(), messageDto.getUrl());
            } else {
                log.warn("Received Kafka message missing memberId or message: {}", message);
            }
        } catch (Exception e) {
            // 메시지 처리 중 예외 발생 시 에러를 로깅합니다.
            log.error("Error processing Kafka message: {}", message, e);
        }
    }
}
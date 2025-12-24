/**
 * @file NotificationConsumer.java
 * @description Kafka의 "notification-topic"으로부터 메시지를 소비(consume)하고,
 * 수신된 메시지를 파싱하여 웹 푸시 알림을 전송하는 컨슈머 클래스입니다.
 */

package com.codehows.daehobe.config.Kafka;

import com.codehows.daehobe.dto.webpush.KafkaNotificationMessageDto;
import com.codehows.daehobe.service.webpush.WebPushService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

/**
 * @class NotificationConsumer
 * @description Kafka의 "notification-topic"으로부터 알림 메시지를 소비하고,
 *              이를 웹 푸시 알림으로 변환하여 사용자에게 전송하는 컨슈머 컴포넌트입니다.
 */
@Slf4j // 로깅을 위한 Lombok 어노테이션
@Component // Spring 컨테이너에 빈으로 등록합니다.
@RequiredArgsConstructor // final 필드에 대한 생성자를 자동으로 생성합니다.
public class NotificationConsumer {

    private final WebPushService webPushService; // 웹 푸시 알림 전송 로직을 담당하는 서비스
    private final ObjectMapper objectMapper; // JSON 메시지를 Java 객체로 역직렬화하기 위한 ObjectMapper

    /**
     * @method listen
     * @description Kafka의 "notification-topic"으로부터 메시지를 리스닝하는 메서드입니다.
     *              수신된 메시지의 키(memberId)와 값을 파싱하여 웹 푸시 알림을 전송합니다.
     * @param {String} memberId - Kafka 메시지의 키로 받은 사용자 ID
     * @param {String} message - Kafka로부터 수신된 JSON 형태의 알림 메시지 문자열
     */
    @KafkaListener(topics = "notification-topic", groupId = "my-group")
    public void listen(@Header(KafkaHeaders.RECEIVED_KEY) String memberId, String message) {
        log.info("Received notification from Kafka for memberId: {}, message: {}", memberId, message);
        try {
            // 수신된 JSON 메시지를 KafkaNotificationMessageDto 객체로 변환합니다.
            KafkaNotificationMessageDto messageDto = objectMapper.readValue(message, KafkaNotificationMessageDto.class);
            String notificationMessage = messageDto.getMessage();

            // 사용자 ID와 알림 메시지가 유효한 경우에만 웹 푸시 알림을 전송합니다.
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
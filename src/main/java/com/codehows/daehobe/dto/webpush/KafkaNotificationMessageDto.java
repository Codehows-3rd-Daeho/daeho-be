/**
 * @file KafkaNotificationMessageDto.java
 * @description Kafka의 "notification-topic"으로 전송되거나 수신되는 알림 메시지의 데이터 전송 객체(DTO)입니다.
 */

package com.codehows.daehobe.dto.webpush;

import lombok.*;

/**
 * @class KafkaNotificationMessageDto
 * @description Kafka의 "notification-topic"을 통해 전송되는 알림 메시지의 내용을 캡슐화하는 DTO입니다.
 *              주로 알림 서비스에서 Kafka로 메시지를 발행하거나, Kafka 컨슈머에서 메시지를 수신할 때 사용됩니다.
 */
@Data// Lombok 어노테이션: getter, setter, toString, equals, hashCode 메서드를 자동으로 생성합니다.
@NoArgsConstructor // Lombok 어노테이션: 인자 없는 기본 생성자를 자동으로 생성합니다.
@AllArgsConstructor // Lombok 어노테이션: 모든 필드를 인자로 받는 생성자를 자동으로 생성합니다.
public class KafkaNotificationMessageDto {
    /**
     * 사용자에게 전송될 알림 메시지의 내용입니다.
     */
    private String message;

    /**
     * 알림 클릭 시 이동할 URL
     */
    private String url;
}
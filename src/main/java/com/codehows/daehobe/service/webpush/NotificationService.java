/**
 * @file NotificationService.java
 * @description 알림 메시지를 Kafka 토픽에 발행(publish)하는 서비스 클래스입니다.
 * 이 서비스는 {@link KafkaNotificationMessageDto} 객체를 JSON 문자열로 변환하여
 * 미리 정의된 Kafka 토픽으로 전송하는 역할을 담당합니다.
 */

package com.codehows.daehobe.service.webpush;

import com.codehows.daehobe.config.Kafka.KafkaTopicConfig;
import com.codehows.daehobe.dto.webpush.KafkaNotificationMessageDto;
import com.codehows.daehobe.dto.webpush.NotificationResponseDto;
import com.codehows.daehobe.entity.member.Member;
import com.codehows.daehobe.entity.notification.Notification;
import com.codehows.daehobe.repository.notification.NotificationRepository;
import com.codehows.daehobe.service.member.MemberService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * @class NotificationService
 * @description 알림 메시지를 Kafka 토픽에 발행하는 서비스입니다.
 * `KafkaNotificationMessageDto` 객체를 JSON 문자열로 변환하여
 * 지정된 Kafka 토픽으로 전송하는 기능을 제공합니다.
 */
@Slf4j // 로깅을 위한 Lombok 어노테이션
@Service // Spring 서비스 컴포넌트임을 나타냅니다.
@Transactional
@RequiredArgsConstructor // final 필드에 대한 생성자를 자동으로 생성합니다.
public class NotificationService {

    /**
     * 알림 메시지를 발행할 Kafka 토픽의 이름입니다.
     * {@link KafkaTopicConfig}에서 정의된 토픽과 일치해야 합니다.
     */
    private static final String NOTIFICATION_TOPIC = "notification-topic";

    /**
     * Kafka 메시지를 전송하기 위한 Spring의 {@link KafkaTemplate}입니다.
     * 이 템플릿을 통해 지정된 토픽으로 메시지를 쉽게 발행할 수 있습니다.
     */
    private final KafkaTemplate<String, String> kafkaTemplate;

    /**
     * Java 객체를 JSON 문자열로 변환하기 위한 {@link ObjectMapper}입니다.
     * Kafka 메시지로 전송하기 전에 {@link KafkaNotificationMessageDto} 객체를 JSON 형태로 직렬화하는 데 사용됩니다.
     */
    private final ObjectMapper objectMapper;
    private final NotificationRepository notificationRepository;
    private final MemberService memberService;

    /**
     * @method sendNotification
     * @description {@link KafkaNotificationMessageDto} 객체를 JSON 문자열로 직렬화하여 Kafka 토픽에 발행합니다.
     * 메시지 발행 중 {@link JsonProcessingException}이 발생하면 예외를 처리하고 로깅합니다.
     */
    public void sendNotification(String memberId, KafkaNotificationMessageDto messageDto) {
        try {
            // DTO 객체를 JSON 문자열로 변환합니다.
            String jsonMessage = objectMapper.writeValueAsString(messageDto);
            log.info("Sending notification to Kafka for memberId: {}, message: {}", memberId, jsonMessage);
            // KafkaTemplate을 사용하여 지정된 토픽으로 메시지를 전송합니다. memberId를 메시지 키로 사용합니다.
            // KafkaTemplate을 통해 Kafka 토픽에 메시지 발행
            //    - memberId: 메시지 키
            //    - jsonMessage: 메시지 값
            kafkaTemplate.send(NOTIFICATION_TOPIC, memberId, jsonMessage);
        } catch (JsonProcessingException e) {
            // JSON 직렬화 중 발생할 수 있는 예외를 처리하고 에러를 로깅합니다.
            log.error("Error serializing notification message to JSON for memberId: {}", memberId, e);
        }
    }

    // db에 알림 저장
    public void saveNotification(Long memberId, KafkaNotificationMessageDto messageDto) {
        Member member = memberService.getMemberById(memberId);
        Notification notification = Notification.builder()
                .member(member)
                .message(messageDto.getMessage())
                .forwardUrl(messageDto.getUrl())
                .isRead(false)
                .build();
        notificationRepository.save(notification);
    }

    // 알림 조회 - 최신순
    public Page<NotificationResponseDto> getMyNotifications(Long memberId, int page, int size) {
        Pageable pageable = PageRequest.of(
                page,
                size,
                Sort.by(Sort.Direction.DESC, "createdAt")
        );
        Page<Notification> notifications = notificationRepository.findByMemberId(memberId, pageable);
        return notifications.map(notification -> {
            Member sender = memberService.getMemberById(notification.getCreatedBy());
            return NotificationResponseDto.fromEntity(notification, sender);
        });
    }

    public void readNotification(Long id) {
        Notification notification = notificationRepository.findById(id).orElseThrow(() -> new EntityNotFoundException("알림이 존재하지 않습니다."));
        notification.setIsRead();
    }

    public int getUnreadCount(Long memberId) {
        return notificationRepository.countByMemberIdAndIsReadFalse(memberId);
    }
}
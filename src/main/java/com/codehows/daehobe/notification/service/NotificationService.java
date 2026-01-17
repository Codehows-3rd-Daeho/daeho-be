/**
 * @file NotificationService.java
 * @description 알림 메시지를 Kafka 토픽에 발행(publish)하는 서비스 클래스입니다.
 * 이 서비스는 {@link KafkaNotificationMessageDto} 객체를 JSON 문자열로 변환하여
 * 미리 정의된 Kafka 토픽으로 전송하는 역할을 담당합니다.
 */

package com.codehows.daehobe.notification.service;

import com.codehows.daehobe.notification.dto.NotificationMessageDto;
import com.codehows.daehobe.notification.dto.NotificationResponseDto;
import com.codehows.daehobe.member.entity.Member;
import com.codehows.daehobe.notification.entity.Notification;
import com.codehows.daehobe.notification.repository.NotificationRepository;
import com.codehows.daehobe.member.service.MemberService;
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

import java.util.Collection;

import static com.codehows.daehobe.common.constant.KafkaConstants.NOTIFICATION_TOPIC;

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
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;
    private final NotificationRepository notificationRepository;
    private final MemberService memberService;

    /**
     * @method sendNotification
     * @description {@link NotificationMessageDto} 객체를 JSON 문자열로 직렬화하여 Kafka 토픽에 발행합니다.
     * 메시지 발행 중 {@link JsonProcessingException}이 발생하면 예외를 처리하고 로깅합니다.
     */
    public void sendNotification(String memberId, NotificationMessageDto messageDto) {
        try {
            String jsonMessage = objectMapper.writeValueAsString(messageDto);
            log.info("Sending notification to Kafka for memberId: {}, message: {}", memberId, jsonMessage);
            // KafkaTemplate을 사용하여 지정된 토픽으로 메시지를 전송합니다. memberId를 메시지 키로 사용합니다.
            // KafkaTemplate을 통해 Kafka 토픽에 메시지 발행
            kafkaTemplate.send(NOTIFICATION_TOPIC, memberId, jsonMessage);
        } catch (JsonProcessingException e) {
            log.error("Error serializing notification message to JSON for memberId: {}", memberId, e);
        }
    }

    public void saveNotification(Long memberId, NotificationMessageDto messageDto) {
        Member member = memberService.getMemberById(memberId);
        Notification notification = Notification.builder()
                .member(member)
                .message(messageDto.getMessage())
                .forwardUrl(messageDto.getUrl())
                .isRead(false)
                .build();
        notificationRepository.save(notification);
    }

    public void notifyMembers(
            Collection<Long> targetMemberIds,
            Long writerId,
            String message,
            String url
    ) {
        NotificationMessageDto dto = new NotificationMessageDto();
        dto.setMessage(message);
        dto.setUrl(url);

        for (Long targetId : targetMemberIds) {
            if (targetId.equals(writerId)) continue; // 작성자 제외

            saveNotification(targetId, dto); // 알림 저장
            sendNotification(String.valueOf(targetId), dto); // 알림 발송
        }
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
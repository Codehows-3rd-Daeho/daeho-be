package com.codehows.daehobe.notification.service;

import com.codehows.daehobe.notification.dto.NotificationMessageDto;
import com.codehows.daehobe.notification.dto.NotificationResponseDto;
import com.codehows.daehobe.member.entity.Member;
import com.codehows.daehobe.notification.entity.Notification;
import com.codehows.daehobe.notification.repository.NotificationRepository;
import com.codehows.daehobe.member.service.MemberService;
import com.codehows.daehobe.notification.webPush.service.WebPushService;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@Transactional
@RequiredArgsConstructor
public class NotificationService {
    private final NotificationRepository notificationRepository;
    private final MemberService memberService;
    private final WebPushService webPushService;
    private final EntityManager entityManager;

    @Async("notificationTaskExecutor")
    public void sendNotification(String memberId, NotificationMessageDto messageDto) {
        log.info("Sending notification for memberId: {}, message: {}", memberId, messageDto.getMessage());
        webPushService.sendNotificationToUser(memberId, messageDto);
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

        List<Long> filteredIds = targetMemberIds.stream()
                .filter(id -> !id.equals(writerId))
                .toList();

        if (filteredIds.isEmpty()) {
            return;
        }

        List<Notification> notifications = filteredIds.stream()
                .map(targetId -> {
                    Member memberRef = entityManager.getReference(Member.class, targetId);
                    return Notification.builder()
                            .member(memberRef)
                            .message(dto.getMessage())
                            .forwardUrl(dto.getUrl())
                            .isRead(false)
                            .build();
                })
                .collect(Collectors.toList());

        // 단일 배치 저장
        notificationRepository.saveAll(notifications);

        // WebPush 알림은 비동기로 개별 전송
        for (Long targetId : filteredIds) {
            sendNotification(String.valueOf(targetId), dto);
        }
    }

    public Page<NotificationResponseDto> getMyNotifications(Long memberId, int page, int size) {
        Pageable pageable = PageRequest.of(
                page,
                size,
                Sort.by(Sort.Direction.DESC, "createdAt")
        );
        Page<Notification> notifications = notificationRepository.findByMemberIdWithCreatedByMember(memberId, pageable);
        return notifications.map(NotificationResponseDto::fromEntityWithCreatedBy);
    }

    public void readNotification(Long id) {
        Notification notification = notificationRepository.findById(id).orElseThrow(() -> new EntityNotFoundException("알림이 존재하지 않습니다."));
        notification.setIsRead();
    }

    public int getUnreadCount(Long memberId) {
        return notificationRepository.countByMemberIdAndIsReadFalse(memberId);
    }
}
